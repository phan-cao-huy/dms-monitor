import asyncio
from datetime import UTC, datetime
from unittest.mock import AsyncMock, Mock

import pytest
from fastapi.testclient import TestClient

from app.core.deps import get_optional_agent_device
from app.db.session import get_db
from app.main import app
from app.models.device import Device, DeviceCommand
from app.models.organization import OrganizationMember
from app.schemas.agent import CommandResultIn
from app.services.alerts import AlertError, acknowledge_alert, resolve_alert
from app.services.devices import (
    DeviceError,
    fetch_pending_commands,
    record_command_result,
    verify_device_credentials,
)


def result(value):
    response = Mock()
    response.scalar_one_or_none.return_value = value
    response.scalars.return_value.all.return_value = value
    return response


def test_archived_agent_auth_only_allows_uninstall_handshake():
    device = Device(device_id=5, archived_at=datetime.now(UTC))
    db = Mock(execute=AsyncMock(return_value=result(device)))
    assert asyncio.run(verify_device_credentials(db, "token", 5)) is device
    sql = str(db.execute.call_args.args[0].compile(compile_kwargs={"literal_binds": True}))
    assert "EXISTS" in sql
    assert "UNINSTALL" in sql
    assert "PENDING" in sql and "EXECUTING" in sql


def test_archived_agent_can_only_fetch_uninstall():
    command = DeviceCommand(command_id=1, command_type="UNINSTALL", status="PENDING")
    db = Mock(execute=AsyncMock(return_value=result([command])), commit=AsyncMock())
    device = Device(device_id=5, archived_at=datetime.now(UTC))
    assert asyncio.run(fetch_pending_commands(db, device)) == [command]
    sql = str(db.execute.call_args.args[0].compile(compile_kwargs={"literal_binds": True}))
    assert "command_type = 'UNINSTALL'" in sql
    assert command.status == "EXECUTING"


def test_archived_agent_cannot_send_heartbeat():
    app.dependency_overrides[get_optional_agent_device] = lambda: Device(archived_at=datetime.now(UTC))
    app.dependency_overrides[get_db] = lambda: None
    try:
        assert TestClient(app).post("/api/v1/agent/heartbeat", json={}).status_code == 403
    finally:
        app.dependency_overrides.pop(get_optional_agent_device, None)
        app.dependency_overrides.pop(get_db, None)


@pytest.mark.parametrize("command_type,allowed", [("UNINSTALL", True), ("REBOOT", False)])
def test_archived_result_is_limited_to_uninstall(command_type, allowed):
    command = DeviceCommand(command_type=command_type)
    db = Mock(execute=AsyncMock(return_value=result(command)), commit=AsyncMock(), refresh=AsyncMock())
    call = record_command_result(db, Device(device_id=5, archived_at=datetime.now(UTC)), 1,
                                 CommandResultIn(status="SUCCESS"))
    if allowed:
        asyncio.run(call)
        db.commit.assert_awaited_once()
    else:
        with pytest.raises(DeviceError, match="uninstall"):
            asyncio.run(call)
        db.commit.assert_not_awaited()


@pytest.mark.parametrize("operation", [resolve_alert, acknowledge_alert])
def test_unassigned_group_member_cannot_modify_alert(operation):
    from app.models.alert import AlertHistory
    db = Mock(execute=AsyncMock(side_effect=[
        result(AlertHistory(alert_id=1, device_id=5)),
        result(Device(device_id=5, org_id=10, group_id=9)),
        result(OrganizationMember(org_id=10, user_id=2, role="Viewer")),
        result(None),
    ]), commit=AsyncMock())
    with pytest.raises(AlertError) as exc:
        asyncio.run(operation(db, 1, 2))
    assert exc.value.status_code == 403
    db.commit.assert_not_awaited()


def test_alert_list_filters_unassigned_groups():
    from app.services.alerts import list_org_alerts
    db = Mock(execute=AsyncMock(side_effect=[
        result(OrganizationMember(org_id=10, user_id=2, role="Viewer")),
        result([]), result([]),
    ]))
    assert asyncio.run(list_org_alerts(db, 10, 2)) == []
    query = db.execute.call_args_list[1].args[0]
    sql = str(query.compile(compile_kwargs={"literal_binds": True}))
    assert "user_group_access.user_id = 2" in sql
    assert "devices.group_id IS NULL" in sql


def test_uninstall_does_not_timeout_while_device_is_offline():
    from unittest.mock import patch

    from app.workers.runner import check_command_timeouts
    db = Mock(execute=AsyncMock(return_value=result([])))
    context = AsyncMock()
    context.__aenter__.return_value = db
    with patch("app.workers.runner.SessionLocal", return_value=context):
        asyncio.run(check_command_timeouts())
    sql = str(db.execute.call_args.args[0].compile(compile_kwargs={"literal_binds": True}))
    assert "command_type != 'UNINSTALL'" in sql
