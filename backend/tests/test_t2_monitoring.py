import asyncio
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch

import pytest
from app.api.v1.routers.synthetic_checks import allowed
from app.models.alert import AlertRule
from app.models.device import Device
from app.schemas.agent import HeartbeatIn
from app.schemas.monitoring import CheckWrite, DeviceEventIn, DeviceEventsIn
from app.services.device_events import ingest_events, list_events
from app.services.synthetic_checks import run_check
from app.services.synthetic_probe import (
    TargetBlocked,
    permitted_ip,
    probe,
    resolve_target,
)
from app.workers.rules import rule_observation
from fastapi import HTTPException
from pydantic import ValidationError


@pytest.mark.parametrize("ip", ["127.0.0.1","169.254.169.254","::1","::ffff:127.0.0.1","0.0.0.0","224.0.0.1","10.0.0.1"])
def test_probe_blocks_unapproved_destinations(ip):
    with patch("app.services.synthetic_probe.settings.SYNTHETIC_ALLOWED_CIDRS", ""):
        assert permitted_ip(ip) is False


def test_explicit_private_cidr_does_not_allow_loopback_or_link_local():
    with patch("app.services.synthetic_probe.settings.SYNTHETIC_ALLOWED_CIDRS", "10.0.0.0/24,127.0.0.0/8,169.254.0.0/16"):
        assert permitted_ip("10.0.0.3")
        assert not permitted_ip("10.0.1.3")
        assert not permitted_ip("127.0.0.1")
        assert not permitted_ip("169.254.169.254")


def test_dns_mixed_public_and_private_records_are_blocked():
    async def run():
        loop=asyncio.get_running_loop()
        with patch.object(loop,"getaddrinfo",AsyncMock(return_value=[(2,1,6,"",("8.8.8.8",80)),(2,1,6,"",("127.0.0.1",80))])), pytest.raises(TargetBlocked):
            await resolve_target("example.test",80)
    asyncio.run(run())


@pytest.mark.parametrize("body", [{"interval_seconds":29},{"timeout_seconds":6},{"target":"http://user:pass@example.com"},{"target":"file:///etc/passwd"},{"target":"http://example.com/\r\nX:bad"}])
def test_invalid_check_configuration(body):
    with pytest.raises(ValidationError):
        CheckWrite.model_validate({"name":"Example","type":"HTTP","target":"https://example.com"}|body)


def test_probe_has_overall_timeout_and_never_exposes_response_body():
    async def delayed(*args):
        await asyncio.sleep(2)
    check=SimpleNamespace(type="TCP",target="example.com:443",timeout_seconds=0.01)
    with patch("app.services.synthetic_probe.resolve_target",new=delayed):
        ok,_,detail=asyncio.run(probe(check))
    assert not ok and detail=="TIMEOUT"


def test_redirect_revalidates_destination_before_connection():
    async def run():
        reader=AsyncMock()
        reader.readuntil.return_value=b"HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/admin\r\n\r\n"
        writer=Mock(drain=AsyncMock())
        async def resolve(host,port):
            if host=="127.0.0.1":
                raise TargetBlocked()
            return "8.8.8.8"
        with patch("app.services.synthetic_probe.resolve_target",new=resolve),patch("app.services.synthetic_probe.stream",AsyncMock(return_value=(reader,writer))) as connect:
            ok,_,detail=await probe(SimpleNamespace(type="HTTP",target="http://example.com",timeout_seconds=5))
            assert not ok and detail=="TARGET_BLOCKED"
            connect.assert_awaited_once()
    asyncio.run(run())


def test_check_viewer_is_rejected_before_network_or_data_access():
    with patch("app.api.v1.routers.synthetic_checks._get_org_member",AsyncMock(return_value=SimpleNamespace(role="Viewer"))), pytest.raises(HTTPException) as exc:
        asyncio.run(allowed(Mock(),10,1))
    assert exc.value.status_code==403


def event(**changes):
    return {"log_name":"System","record_id":42,"event_id":1000,"level":"ERROR","source":"Test","message":"Example",
            "occurred_at":datetime.now(UTC).isoformat()}|changes


def test_event_batch_and_message_limits_and_security_filter():
    with pytest.raises(ValidationError):
        DeviceEventsIn(events=[event()]*51)
    with pytest.raises(ValidationError):
        DeviceEventIn(**event(message="x"*1001))
    with pytest.raises(ValidationError):
        DeviceEventIn(**event(log_name="Security",event_id=4624,level="AUDIT_FAILURE"))


def test_old_events_are_acknowledged_but_not_stored():
    db=Mock(execute=AsyncMock(),commit=AsyncMock())
    old=DeviceEventIn(**event(occurred_at=(datetime.now(UTC)-timedelta(days=30)).isoformat()))
    asyncio.run(ingest_events(db,SimpleNamespace(device_id=1),[old]))
    db.execute.assert_not_awaited()
    db.commit.assert_awaited_once()


def test_event_list_checks_device_access_before_querying():
    from app.services.devices import DeviceError
    db=Mock(execute=AsyncMock())
    with patch("app.services.device_events.check_user_device_access",AsyncMock(side_effect=DeviceError(403,"Denied"))), pytest.raises(DeviceError):
        asyncio.run(list_events(db,10,1,None,None,50))
    db.execute.assert_not_awaited()


def test_heartbeat_parses_monitoring_fields_without_fabricating_health():
    payload=HeartbeatIn(metrics={"disks_health":[{"name":"Disk 0","healthy":None}],"connections":[],"t2_collected_at":"2026-10-28T00:00:00Z"})
    assert payload.disks_health[0].healthy is None
    assert payload.connections==[]
    assert "connections" in payload.model_fields_set


def test_disk_health_rule_requires_real_failure():
    now=datetime.now(UTC).replace(tzinfo=None)
    device=Device(device_id=1,archived_at=None,is_monitoring=True,last_heartbeat=now,static_info={"disks_health":[{"healthy":None}],"t2_collected_at":now.isoformat()+"Z"})
    rule=AlertRule(metric_type="DISK_HEALTH",condition=">=",threshold=1)
    assert rule_observation(rule,device,None,now)==(None,None)
    device.static_info={"disks_health":[{"healthy":False}],"t2_collected_at":now.isoformat()+"Z"}
    assert rule_observation(rule,device,None,now)==(True,1)


def test_check_incident_opens_after_threshold_and_resolves_on_recovery():
    check=SimpleNamespace(check_id=1,timeout_seconds=5,consecutive_failures=1,failure_threshold=3,incident_open=False,last_checked_at=None)
    result=Mock(scalar_one_or_none=Mock(return_value=None))
    db=Mock(execute=AsyncMock(return_value=result),commit=AsyncMock())
    with patch("app.services.synthetic_checks.probe",AsyncMock(return_value=(False,5,"TIMEOUT"))):
        asyncio.run(run_check(db,check))
        assert check.consecutive_failures==2 and not check.incident_open
        asyncio.run(run_check(db,check))
        assert check.consecutive_failures==3 and check.incident_open
    incident=SimpleNamespace(resolved_at=None)
    result.scalar_one_or_none.return_value=incident
    with patch("app.services.synthetic_checks.probe",AsyncMock(return_value=(True,5,"HTTP_200"))):
        asyncio.run(run_check(db,check))
    assert check.consecutive_failures==0 and not check.incident_open
    assert incident.resolved_at is not None
