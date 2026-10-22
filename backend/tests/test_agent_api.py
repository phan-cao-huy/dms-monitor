from fastapi.testclient import TestClient

from app.core.deps import get_current_agent_device, get_optional_agent_device
from app.db.session import get_db
from app.main import app
from app.models.device import Device, DeviceCommand
from app.services.devices import generate_device_token


def test_agent_heartbeat_with_header_token():
    raw_token, token_hash, prefix = generate_device_token()
    mock_device = Device(
        device_id=1,
        org_id=1,
        device_name="Test-PC",
        secret_token_hash=token_hash,
        secret_token_prefix=prefix,
        status="Offline",
        offline_threshold_minutes=5,
    )

    async def override_device():
        return mock_device

    async def override_db():
        class MockSession:
            def add(self, obj):
                pass

            async def commit(self):
                pass

        yield MockSession()

    app.dependency_overrides[get_optional_agent_device] = override_device
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        payload = {
            "cpu_usage": 45.2,
            "ram_usage": 60.5,
            "disk_usage": 70.1,
            "net_in_kbps": 100.0,
            "net_out_kbps": 50.0,
        }
        res = client.post(
            "/api/v1/agent/heartbeat",
            json=payload,
            headers={"X-Device-Token": raw_token},
        )
        assert res.status_code == 202
        assert res.json() == {"accepted": True}
        assert mock_device.status == "Online"
    finally:
        app.dependency_overrides.pop(get_optional_agent_device, None)
        app.dependency_overrides.pop(get_db, None)


def test_agent_heartbeat_nested_payload_like_go_agent():
    raw_token, token_hash, prefix = generate_device_token()
    mock_device = Device(
        device_id=2,
        org_id=1,
        device_name="Agent-Go",
        secret_token_hash=token_hash,
        secret_token_prefix=prefix,
        status="Offline",
        offline_threshold_minutes=5,
    )

    async def override_device():
        return mock_device

    async def override_db():
        class MockSession:
            def add(self, obj):
                pass

            async def commit(self):
                pass

        yield MockSession()

    app.dependency_overrides[get_optional_agent_device] = override_device
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        payload = {
            "device_id": 2,
            "secret_token": raw_token,
            "metrics": {
                "cpu_usage": 33.3,
                "ram_usage": 55.5,
                "disk_usage": 88.8,
                "processes": [{"pid": 1234, "name": "python.exe", "cpu_pct": 5.0}],
            },
            "static_info": {
                "hostname": "workstation-01",
                "os_name": "Windows",
                "cpu_cores": 8,
            },
        }
        res = client.post(
            "/api/v1/agent/heartbeat",
            json=payload,
            headers={"X-Device-Token": raw_token},
        )
        assert res.status_code == 202
        assert res.json() == {"accepted": True}
        assert mock_device.status == "Online"
        assert mock_device.static_info == {
            "hostname": "workstation-01",
            "os_name": "Windows",
            "cpu_cores": 8,
        }
        assert mock_device.top_processes == [
            {"pid": 1234, "name": "python.exe", "cpu_pct": 5.0}
        ]
    finally:
        app.dependency_overrides.pop(get_optional_agent_device, None)
        app.dependency_overrides.pop(get_db, None)


def test_agent_pending_commands_and_go_agent_compatibility():
    raw_token, token_hash, prefix = generate_device_token()
    mock_device = Device(
        device_id=3,
        org_id=1,
        device_name="Test-Device",
        secret_token_hash=token_hash,
        secret_token_prefix=prefix,
    )

    cmd = DeviceCommand(
        command_id=99,
        device_id=3,
        created_by=1,
        command_type="REBOOT",
        parameters={"delay_seconds": 10},
        reason="Scheduled maintenance",
        status="PENDING",
    )

    async def override_device():
        return mock_device

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                class MockResult:
                    def scalars(self):
                        class MockScalars:
                            def all(self):
                                return [cmd]

                        return MockScalars()

                return MockResult()

            async def commit(self):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_agent_device] = override_device
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)

        # Standard caller: returns array
        res_standard = client.get(
            "/api/v1/agent/commands",
            headers={"Authorization": f"Bearer {raw_token}"},
        )
        assert res_standard.status_code == 200
        assert res_standard.json() == [
            {
                "command_id": 99,
                "command_type": "REBOOT",
                "parameters": {"delay_seconds": 10},
            }
        ]

        # Go-http-client caller: returns {"commands": [...]}
        res_go = client.get(
            "/api/v1/agent/commands",
            headers={
                "Authorization": f"Bearer {raw_token}",
                "User-Agent": "Go-http-client/1.1",
            },
        )
        assert res_go.status_code == 200
        assert res_go.json() == {
            "commands": [
                {
                    "command_id": 99,
                    "command_type": "REBOOT",
                    "parameters": {"delay_seconds": 10},
                }
            ]
        }
    finally:
        app.dependency_overrides.pop(get_current_agent_device, None)
        app.dependency_overrides.pop(get_db, None)


def test_agent_command_result_post_and_put():
    _raw_token, token_hash, prefix = generate_device_token()
    mock_device = Device(
        device_id=4,
        org_id=1,
        device_name="Test-Device",
        secret_token_hash=token_hash,
        secret_token_prefix=prefix,
    )
    cmd = DeviceCommand(
        command_id=101,
        device_id=4,
        created_by=1,
        command_type="KILL_PROCESS",
        parameters={"pid": 1234},
        reason="Hanging",
        status="EXECUTING",
    )

    async def override_device():
        return mock_device

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                class MockResult:
                    def scalar_one_or_none(self):
                        return cmd

                return MockResult()

            async def commit(self):
                pass

            async def refresh(self, obj):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_agent_device] = override_device
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)

        # POST result
        res_post = client.post(
            "/api/v1/agent/commands/101/result",
            json={"status": "SUCCESS", "output": "Killed PID 1234"},
        )
        assert res_post.status_code == 200
        assert res_post.json() == {"ok": True}
        assert cmd.status == "SUCCESS"
        assert cmd.output == "Killed PID 1234"

        # PUT result (Go agent uses PUT)
        res_put = client.put(
            "/api/v1/agent/commands/101/result",
            json={"status": "FAILED", "error_message": "Access denied"},
        )
        assert res_put.status_code == 200
        assert res_put.json() == {"ok": True}
        assert cmd.status == "FAILED"
        assert cmd.output == "Access denied"
    finally:
        app.dependency_overrides.pop(get_current_agent_device, None)
        app.dependency_overrides.pop(get_db, None)


def test_agent_heartbeat_high_ram_triggers_warning_alert():
    from app.models.alert import AlertHistory

    raw_token, token_hash, prefix = generate_device_token()
    mock_device = Device(
        device_id=10,
        org_id=1,
        device_name="HighRAM-PC",
        secret_token_hash=token_hash,
        secret_token_prefix=prefix,
        status="Online",
        offline_threshold_minutes=5,
    )

    added_objects = []

    async def override_device():
        return mock_device

    async def override_db():
        class MockSession:
            def add(self, obj):
                added_objects.append(obj)

            async def commit(self):
                pass

            async def execute(self, stmt):
                class MockResult:
                    def scalar_one_or_none(self):
                        return None

                return MockResult()

        yield MockSession()

    app.dependency_overrides[get_optional_agent_device] = override_device
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        payload = {
            "cpu_usage": 40.0,
            "ram_usage": 91.5,
            "disk_usage": 50.0,
        }
        res = client.post(
            "/api/v1/agent/heartbeat",
            json=payload,
            headers={"X-Device-Token": raw_token},
        )
        assert res.status_code == 202
        assert mock_device.status == "Warning"

        # Check AlertHistory was created
        alert = next((obj for obj in added_objects if isinstance(obj, AlertHistory)), None)
        assert alert is not None
        assert alert.severity == "WARNING"
        assert "RAM vượt ngưỡng 90%" in alert.message
        assert alert.metric_value == 91.5
    finally:
        app.dependency_overrides.pop(get_optional_agent_device, None)
        app.dependency_overrides.pop(get_db, None)

