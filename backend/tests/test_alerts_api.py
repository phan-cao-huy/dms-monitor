from datetime import UTC, datetime
from fastapi.testclient import TestClient

from app.core.deps import get_current_user
from app.db.session import get_db
from app.main import app
from app.models.alert import AlertHistory, AlertRule
from app.models.auth import User
from app.models.organization import OrganizationMember


def _now():
    return datetime.now(UTC).replace(tzinfo=None)


def _mock_user():
    return User(
        user_id=1,
        username="admin_user",
        email="admin@example.com",
        password_hash="hash",
    )


def test_create_and_list_alert_rules():
    user = _mock_user()
    member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Admin")
    rule = AlertRule(
        rule_id=1,
        group_id=None,
        device_id=5,
        rule_name="High CPU Alert",
        metric_type="CPU",
        condition=">",
        threshold=85.0,
        severity="WARNING",
        duration_minutes=2,
        cooldown_minutes=15,
        is_active=True,
        created_at=_now(),
    )

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                sql = str(stmt).lower()

                class MockResult:
                    def scalar_one_or_none(self):
                        if "devices" in sql:
                            return 5
                        return member

                    def scalars(self):
                        class MockScalars:
                            def all(self):
                                if "devices" in sql:
                                    return [5]
                                if "device_groups" in sql:
                                    return []
                                return [rule]

                        return MockScalars()

                return MockResult()

            def add(self, obj):
                obj.rule_id = 1
                obj.created_at = _now()
                obj.is_active = True

            async def commit(self):
                pass

            async def refresh(self, obj):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)

        # Create alert rule
        res_create = client.post(
            "/api/v1/orgs/10/alert-rules",
            json={
                "device_id": 5,
                "rule_name": "High CPU Alert",
                "metric_type": "CPU",
                "condition": ">",
                "threshold": 85.0,
                "severity": "WARNING",
                "duration_minutes": 2,
                "cooldown_minutes": 15,
            },
        )
        assert res_create.status_code == 201
        data = res_create.json()
        assert data["rule_id"] == 1
        assert data["metric_type"] == "CPU"
        assert data["threshold"] == 85.0

        # List alert rules
        res_list = client.get("/api/v1/orgs/10/alert-rules")
        assert res_list.status_code == 200
        assert len(res_list.json()) == 1
        assert res_list.json()[0]["rule_name"] == "High CPU Alert"
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)


def test_list_alerts_and_resolve_and_acknowledge():
    user = _mock_user()
    member = OrganizationMember(member_id=1, org_id=10, user_id=1, role="Viewer")
    alert = AlertHistory(
        alert_id=20,
        device_id=5,
        rule_id=1,
        severity="CRITICAL",
        message="Device 'Build-Agent' offline",
        metric_value=10.0,
        threshold_value=5.0,
        notification_status="PENDING",
        created_at=_now(),
    )

    async def override_user():
        return user

    async def override_db():
        class MockSession:
            async def execute(self, stmt):
                sql = str(stmt).lower()

                class MockResult:
                    def scalar_one_or_none(self):
                        if "devices" in sql:
                            return 10
                        if "alert_history" in sql:
                            return alert
                        return member

                    def scalars(self):
                        class MockScalars:
                            def all(self):
                                if "devices" in sql:
                                    return [5]
                                return [alert]

                        return MockScalars()

                return MockResult()

            async def commit(self):
                pass

            async def refresh(self, obj):
                pass

        yield MockSession()

    app.dependency_overrides[get_current_user] = override_user
    app.dependency_overrides[get_db] = override_db
    try:
        client = TestClient(app)

        # List alerts
        res_list = client.get("/api/v1/orgs/10/alerts")
        assert res_list.status_code == 200
        assert len(res_list.json()) == 1
        assert res_list.json()[0]["alert_id"] == 20

        # Acknowledge alert
        res_ack = client.post("/api/v1/alerts/20/acknowledge")
        assert res_ack.status_code == 200
        assert alert.acknowledged_by == 1
        assert alert.acknowledged_at is not None

        # Resolve alert
        res_resolve = client.post("/api/v1/alerts/20/resolve")
        assert res_resolve.status_code == 200
        assert alert.resolved_at is not None
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)
