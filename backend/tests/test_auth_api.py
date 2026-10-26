from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.core.deps import get_current_user
from app.main import app
from app.models.auth import User


def test_get_current_user_returns_public_profile():
    async def current_user_override() -> User:
        return User(
            user_id=7,
            username="linh",
            email="linh@example.com",
            password_hash="never-return-this",
            full_name="Linh Nguyen",
        )

    app.dependency_overrides[get_current_user] = current_user_override
    try:
        response = TestClient(app).get("/api/v1/auth/me")
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 200
    assert response.json() == {
        "user_id": 7,
        "username": "linh",
        "email": "linh@example.com",
        "full_name": "Linh Nguyen",
    }
    assert "password_hash" not in response.json()


def test_get_current_user_detail_returns_full_profile():
    async def current_user_override() -> User:
        return User(
            user_id=7,
            username="linh",
            email="linh@example.com",
            password_hash="never-return-this",
            full_name="Linh Nguyen",
            avatar_url="https://example.com/avatar.png",
            is_email_verified=True,
        )

    app.dependency_overrides[get_current_user] = current_user_override
    try:
        response = TestClient(app).get("/api/v1/auth/me/detail")
    finally:
        app.dependency_overrides.pop(get_current_user, None)

    assert response.status_code == 200
    data = response.json()
    assert data["user_id"] == 7
    assert "phone" not in data
    assert data["avatar_url"] == "https://example.com/avatar.png"
    assert data["is_email_verified"] is True
    assert "password_hash" not in data


def test_users_endpoint_only_returns_current_user():
    from app.db.session import get_db

    async def current_user_override() -> User:
        return User(
            user_id=7,
            username="linh",
            email="linh@example.com",
            password_hash="never-return-this",
            full_name="Linh Nguyen",
        )

    async def fake_db():
        yield None

    app.dependency_overrides[get_current_user] = current_user_override
    app.dependency_overrides[get_db] = fake_db

    try:
        response = TestClient(app).get("/api/v1/auth/users")
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_db, None)
    assert response.status_code == 200
    users = response.json()
    assert len(users) == 1
    assert users[0]["user_id"] == 7
    assert "password_hash" not in users[0]


def test_fcm_token_is_saved_to_the_matching_refresh_session():
    from app.db.session import get_db

    async def current_user_override() -> User:
        return User(
            user_id=7,
            username="linh",
            email="linh@example.com",
            password_hash="never-return-this",
        )

    async def fake_db():
        yield None

    app.dependency_overrides[get_current_user] = current_user_override
    app.dependency_overrides[get_db] = fake_db
    with patch(
        "app.api.v1.routers.auth.save_fcm_token",
        new=AsyncMock(return_value=True),
    ) as save_token:
        try:
            response = TestClient(app).post(
                "/api/v1/auth/fcm-token",
                json={
                    "refresh_token": "session-refresh-token",
                    "fcm_token": "device-fcm-token",
                    "device_info": "Android app",
                },
            )
        finally:
            app.dependency_overrides.pop(get_current_user, None)
            app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 204
    save_token.assert_awaited_once_with(
        None, 7, "session-refresh-token", "device-fcm-token", "Android app"
    )
