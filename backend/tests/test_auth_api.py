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
