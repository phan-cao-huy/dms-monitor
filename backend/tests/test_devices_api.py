from datetime import UTC, datetime

from fastapi.testclient import TestClient

from app.core.deps import get_current_user
from app.db.session import get_db
from app.main import app
from app.models.auth import User
from app.models.device import Device
from app.models.organization import OrganizationMember


def _now():
    return datetime.now(UTC).replace(tzinfo=None)


def _mock_user():
    return User(
        user_id=1,
        username="owner_user",
        email="owner@example.com",
        password_hash="hash",
    )


def test_create_device_returns_secret_token_once():
    user = _mock_user()
    org_member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Owner")

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                class MockResult:
                    def scalar_one_or_none(self):
                        return org_member

                return MockResult()

            def add(self, obj):
                obj.device_id = 42
                obj.created_at = datetime(2026, 1, 1, 0, 0, 0)

            async def commit(self):
                pass

            async def refresh(self, obj):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        res = client.post(
            "/api/v1/orgs/10/devices",
            json={
                "device_name": "Ubuntu-Server",
                "description": "Production DB host",
                "offline_threshold_minutes": 5,
            },
        )
        assert res.status_code == 201
        data = res.json()
        assert data["device_id"] == 42
        assert data["org_id"] == 10
        assert data["device_name"] == "Ubuntu-Server"
        assert data["secret_token"].startswith("dms_")
        assert len(data["secret_token_prefix"]) == 8
        assert data["status"] == "Offline"
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)


def test_list_devices_returns_dynamic_status():
    user = _mock_user()
    member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Owner")
    device = Device(
        device_id=1,
        org_id=10,
        device_name="Dev-PC",
        description="Office desktop",
        secret_token_hash="hash",
        secret_token_prefix="dms_1234",
        status="Online",
        last_heartbeat=_now(),
        offline_threshold_minutes=5,
        created_at=datetime(2026, 1, 1),
        updated_at=datetime(2026, 1, 1),
    )

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                sql = str(stmt).lower()

                class MockResult:
                    def scalars(self):
                        class MockScalars:
                            def all(self):
                                if "org_members" in sql:
                                    return [member]
                                if "user_group_access" in sql:
                                    return []
                                if "device_groups" in sql:
                                    return []
                                return [device]

                        return MockScalars()

                return MockResult()

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        res = client.get("/api/v1/devices")
        assert res.status_code == 200
        data = res.json()
        assert len(data) == 1
        assert data[0]["device_id"] == 1
        assert data[0]["device_name"] == "Dev-PC"
        assert data[0]["status"] in ("Online", "Offline")
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)


def test_get_device_detail_includes_static_info_and_processes():
    user = _mock_user()
    member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Admin")
    device = Device(
        device_id=5,
        org_id=10,
        device_name="Build-Agent",
        secret_token_hash="hash",
        secret_token_prefix="dms_5555",
        status="Online",
        last_heartbeat=_now(),
        offline_threshold_minutes=5,
        static_info={"hostname": "ci-runner", "os_name": "Linux"},
        top_processes=[{"pid": 99, "name": "make", "cpu_pct": 80.0}],
        created_at=datetime(2026, 1, 1),
        updated_at=datetime(2026, 1, 1),
    )

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                sql = str(stmt).lower()

                class MockResult:
                    def scalar_one_or_none(self):
                        if "device_metrics" in sql:
                            return None
                        return device if "devices" in sql else member

                return MockResult()

            async def get(self, entity, ident):
                return None

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        res = client.get("/api/v1/devices/5")
        assert res.status_code == 200
        data = res.json()
        assert data["device_id"] == 5
        assert data["static_info"] == {"hostname": "ci-runner", "os_name": "Linux"}
        assert data["top_processes"] == [
            {"pid": 99, "name": "make", "cpu_pct": 80.0}
        ]

        # Also test processes snapshot endpoint
        res_proc = client.get("/api/v1/devices/5/processes")
        assert res_proc.status_code == 200
        assert res_proc.json() == [{"pid": 99, "name": "make", "cpu_pct": 80.0}]
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)


def test_post_device_command_validation_and_creation():
    user = _mock_user()
    member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Owner")
    device = Device(device_id=5, org_id=10, device_name="Build-Agent")

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                class MockResult:
                    def scalar_one_or_none(self):
                        return device if "devices" in str(stmt).lower() else member

                return MockResult()

            def add(self, obj):
                obj.command_id = 77
                obj.created_at = datetime(2026, 1, 1)

            async def commit(self):
                pass

            async def refresh(self, obj):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)

        # Missing reason
        res_fail = client.post(
            "/api/v1/devices/5/commands",
            json={"command_type": "REBOOT"},
        )
        assert res_fail.status_code == 422

        # Invalid command_type
        res_invalid = client.post(
            "/api/v1/devices/5/commands",
            json={"command_type": "DROP_DATABASE", "reason": "Accident"},
        )
        assert res_invalid.status_code == 422

        # Valid KILL_PROCESS
        res_ok = client.post(
            "/api/v1/devices/5/commands",
            json={
                "command_type": "KILL_PROCESS",
                "parameters": {"pid": 4321},
                "reason": "Process consumed 100% CPU",
            },
        )
        assert res_ok.status_code == 201
        data = res_ok.json()
        assert data["command_id"] == 77
        assert data["command_type"] == "KILL_PROCESS"
        assert data["status"] == "PENDING"
        assert data["reason"] == "Process consumed 100% CPU"
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)



def test_toggle_monitoring_and_delete_sends_uninstall():
    user = _mock_user()
    org_member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Owner")
    device = Device(
        device_id=5,
        org_id=10,
        device_name="Toggle-PC",
        description="Test toggle",
        secret_token_hash="hash",
        secret_token_prefix="dms_5555",
        status="Online",
        is_monitoring=True,
        last_heartbeat=_now(),
        offline_threshold_minutes=5,
        created_at=datetime(2026, 1, 1),
        updated_at=datetime(2026, 1, 1),
    )

    added_objects = []

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                sql = str(stmt).lower()
                class MockResult:
                    def scalar_one_or_none(self):
                        if "devices" in sql:
                            return device
                        return org_member

                return MockResult()

            async def get(self, model, ident):
                return device

            def add(self, obj):
                added_objects.append(obj)

            async def commit(self):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)
        # 1. Toggle monitoring off
        res_off = client.post("/api/v1/devices/5/toggle-monitoring", json={"is_monitoring": False})
        assert res_off.status_code == 200
        assert res_off.json()["is_monitoring"] is False
        assert res_off.json()["status"] == "Paused"

        # Check PAUSE_MONITORING command enqueued
        assert any(getattr(o, "command_type", None) == "PAUSE_MONITORING" for o in added_objects)

        # 2. Toggle monitoring back on
        res_on = client.post("/api/v1/devices/5/toggle-monitoring", json={"is_monitoring": True})
        assert res_on.status_code == 200
        assert res_on.json()["is_monitoring"] is True
        assert res_on.json()["status"] == "Online"

        # Check RESUME_MONITORING command enqueued
        assert any(getattr(o, "command_type", None) == "RESUME_MONITORING" for o in added_objects)

        # 3. Delete device sends UNINSTALL command
        res_del = client.delete("/api/v1/devices/5")
        assert res_del.status_code == 204
        assert device.status == "Archived"
        assert device.archived_at is not None

        # Check UNINSTALL command enqueued
        assert any(getattr(o, "command_type", None) == "UNINSTALL" for o in added_objects)
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)
