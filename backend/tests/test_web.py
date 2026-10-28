from fastapi.testclient import TestClient

from app.main import app


def test_web_home_and_assets_share_api_server():
    client = TestClient(app)
    home = client.get("/")
    assert home.status_code == 200
    assert 'lang="vi"' in home.text
    assert 'src="./app.js?v=' in home.text
    assert client.get("/api.js").status_code == 200
    assert "https://dms.ky215.io.vn/api/v1/" in client.get("/api.js").text
    assert client.get("/api/v1/health").json() == {"status": "ok"}
    assert client.get("/api/v1/orgs").status_code == 401
