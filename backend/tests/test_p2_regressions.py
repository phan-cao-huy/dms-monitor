import asyncio
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, Mock, patch

import pytest
from pydantic import ValidationError

from app.models.alert import AlertRule, AlertRuleState
from app.models.device import Device, DeviceMetric
from app.schemas.alert import AlertRuleCreate
from app.schemas.device import DeviceCommandCreate
from app.services.devices import create_device_command
from app.workers.rules import rule_observation, update_observation


@pytest.mark.parametrize(
    "command,enabled,status",
    [
        ("PAUSE_MONITORING", False, "Paused"),
        ("RESUME_MONITORING", True, "Offline"),
    ],
)
def test_command_endpoint_synchronizes_monitoring(command, enabled, status):
    device = Device(device_id=1, is_monitoring=not enabled, last_heartbeat=None)
    db = Mock(commit=AsyncMock(), refresh=AsyncMock())
    with patch(
        "app.services.devices.check_user_device_access",
        new=AsyncMock(return_value=(device, None)),
    ):
        asyncio.run(
            create_device_command(
                db, 1, 2, DeviceCommandCreate(command_type=command, reason="test")
            )
        )
    assert device.is_monitoring is enabled
    assert device.status == status
    db.commit.assert_awaited_once()


@pytest.mark.parametrize("kind", ["CPU", "RAM", "DISK", "NET_IN", "NET_OUT"])
def test_numeric_rule_uses_threshold_and_fresh_data(kind):
    now = datetime.now(UTC).replace(tzinfo=None)
    rule = AlertRule(metric_type=kind, condition=">", threshold=80)
    device = Device(last_heartbeat=now, is_monitoring=True)
    metric = DeviceMetric(
        cpu_usage=90,
        ram_usage=90,
        disk_usage=90,
        net_in_kbps=90,
        net_out_kbps=90,
        created_at=now,
    )
    assert rule_observation(rule, device, metric, now) == (True, 90)
    metric.created_at = now - timedelta(minutes=3)
    assert rule_observation(rule, device, metric, now) == (None, None)


def test_duration_resets_when_observation_missing_or_worker_was_down():
    now = datetime.now(UTC).replace(tzinfo=None)
    state = AlertRuleState()
    update_observation(state, True, now)
    update_observation(state, True, now + timedelta(minutes=1))
    assert state.active_since == now
    update_observation(state, True, now + timedelta(minutes=4))
    assert state.active_since == now + timedelta(minutes=4)
    update_observation(state, None, now + timedelta(minutes=5))
    assert state.active_since is None


def test_service_rule_never_treats_unknown_as_stopped():
    now = datetime.now(UTC).replace(tzinfo=None)
    rule = AlertRule(metric_type="SERVICE", service_name="Spooler")
    device = Device(last_heartbeat=now, watch_status={})
    assert rule_observation(rule, device, None, now) == (None, None)
    device.watch_status = {"services": {"Spooler": False}}
    assert rule_observation(rule, device, None, now) == (True, 1)


@pytest.mark.parametrize(
    "payload",
    [
        {"device_id": 1, "metric_type": "CPU"},
        {"device_id": 1, "metric_type": "SERVICE"},
        {"device_id": 1, "metric_type": "PROCESS_MISSING"},
        {"device_id": 1, "group_id": 1, "metric_type": "CPU", "threshold": 80},
    ],
)
def test_invalid_alert_rules_rejected_before_database(payload):
    with pytest.raises(ValidationError):
        AlertRuleCreate(**payload)


def test_notifications_disabled_without_credentials():
    from app.workers.notifications import send_pending_notifications

    with (
        patch("app.workers.notifications.settings") as settings,
        patch("app.workers.notifications.SessionLocal") as session,
    ):
        settings.FCM_ENABLED = False
        asyncio.run(send_pending_notifications())
        session.assert_not_called()


def test_watch_config_includes_rules_and_device_configuration():
    from app.services.devices import agent_watch_config

    rules = [
        AlertRule(metric_type="SERVICE", service_name="Spooler"),
        AlertRule(metric_type="PROCESS_MISSING", process_name="worker.exe"),
    ]
    response = Mock()
    response.scalars.return_value.all.return_value = rules
    db = Mock(execute=AsyncMock(return_value=response))
    device = Device(
        device_id=1,
        group_id=2,
        watch_config={"services": [{"name": "Spooler"}], "processes": ["another.exe"]},
    )
    assert asyncio.run(agent_watch_config(db, device)) == {
        "services": [{"name": "Spooler"}],
        "processes": ["another.exe", "worker.exe"],
    }


@pytest.mark.parametrize("failed,expected", [(0, "SENT"), (1, "FAILED")])
def test_fcm_delivery_respects_scope_and_records_result(failed, expected):
    from app.models.alert import AlertHistory
    from app.workers.notifications import send_pending_notifications

    alert = AlertHistory(
        alert_id=1, device_id=2, message="test", notification_status="PENDING"
    )
    responses = [Mock(), Mock()]
    responses[0].scalars.return_value.all.return_value = [alert]
    responses[1].scalars.return_value.all.return_value = ["test-token"]
    db = Mock(
        execute=AsyncMock(side_effect=responses),
        get=AsyncMock(return_value=Device(device_id=2, org_id=3, group_id=4)),
        commit=AsyncMock(),
    )
    context = AsyncMock()
    context.__aenter__.return_value = db
    with (
        patch("app.workers.notifications.settings") as config,
        patch("app.workers.notifications.firebase_app", return_value=Mock()),
        patch("app.workers.notifications.SessionLocal", return_value=context),
        patch(
            "firebase_admin.messaging.send_each_for_multicast",
            return_value=Mock(failure_count=failed),
        ) as send,
    ):
        config.FCM_ENABLED = True
        config.FCM_CREDENTIALS_FILE = "unused-test-credential"
        asyncio.run(send_pending_notifications())
    send.assert_called_once()
    sql = str(
        db.execute.call_args_list[1]
        .args[0]
        .compile(compile_kwargs={"literal_binds": True})
    )
    assert "org_members.org_id = 3" in sql
    assert "user_group_access.group_id = 4" in sql
    assert "auth_tokens.used_at IS NULL" in sql
    assert alert.notification_status == expected


def test_only_one_worker_starts_scheduler():
    from app.workers.runner import worker_lifespan

    contexts = [AsyncMock(), AsyncMock()]
    for context, acquired in zip(contexts, (True, False), strict=True):
        conn = context.__aenter__.return_value
        response = Mock()
        response.scalar.return_value = acquired
        conn.execute.return_value = response
    engine = Mock(
        connect=Mock(side_effect=contexts), begin=Mock(return_value=AsyncMock())
    )
    scheduler = Mock(running=True)

    async def run():
        async with worker_lifespan() as first:
            assert first is scheduler
            async with worker_lifespan() as second:
                assert second is None

    with (
        patch("app.workers.runner.engine", engine),
        patch("app.workers.runner.manage_partitions", new=AsyncMock()) as partition,
        patch("app.workers.runner.build_scheduler", return_value=scheduler),
    ):
        asyncio.run(run())
    scheduler.start.assert_called_once()
    scheduler.shutdown.assert_called_once()
    partition.assert_awaited_once()
