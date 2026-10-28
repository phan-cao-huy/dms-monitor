import asyncio
import smtplib
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest
from app.core.deps import get_current_user
from app.db.session import get_db
from app.main import app
from app.models.email_channel import EmailChannel, EmailDelivery
from app.schemas.email_channel import EmailConfig
from app.services.email_channels import (
    channel_out,
    decrypt_config,
    encrypt_config,
    safe_delivery_error,
    send_email,
)
from app.workers import email_notifications
from app.workers.rules import rule_observation
from cryptography.fernet import Fernet
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError


def config():
    return {"host": "smtp.gmail.com", "port": 587, "username": "sender@example.com",
            "password": "test-secret", "from_email": "sender@example.com",
            "recipients": ["recipient@example.com"], "language": "vi"}


def test_config_is_encrypted_and_password_is_never_returned():
    with patch("app.services.email_channels.settings.NOTIFICATION_ENCRYPTION_KEY", Fernet.generate_key().decode()):
        encrypted = encrypt_config(config())
        assert "test-secret" not in encrypted and "recipient@" not in encrypted
        c = EmailChannel(channel_id=1, name="Mail", enabled=True, config_encrypted=encrypted)
        assert decrypt_config(c) == config()
        assert "password" not in channel_out(c)["config"]
        assert channel_out(c)["password_set"] is True


@pytest.mark.parametrize("changes", [{"port":25}, {"host":"https://example.com"},
                                      {"recipients":[]}, {"recipients":["invalid"]}, {"username":"bad\r\nname"}])
def test_invalid_smtp_config_is_rejected(changes):
    with pytest.raises(ValidationError):
        EmailConfig.model_validate(config() | changes)


def test_missing_encryption_key_fails_closed():
    with patch("app.services.email_channels.settings.NOTIFICATION_ENCRYPTION_KEY", None):
        with pytest.raises(HTTPException) as exc:
            encrypt_config(config())
        assert exc.value.status_code == 503


@pytest.mark.parametrize("port", [465, 587])
def test_smtp_uses_tls_before_credentials_and_sets_timeout(port):
    client = MagicMock()
    client.__enter__.return_value = client
    client.send_message.return_value = {}
    with patch("app.services.email_channels.smtplib.SMTP", return_value=client) as plain, patch("app.services.email_channels.smtplib.SMTP_SSL", return_value=client) as tls:
        send_email(config() | {"port":port}, "DMS test\r\nInjected: bad", "Alert body")
        constructor = tls if port == 465 else plain
        assert constructor.call_args.kwargs["timeout"] == 10
        if port == 587:
            names = [c[0] for c in client.method_calls]
            assert names.index("starttls") < names.index("login")
        message = client.send_message.call_args.args[0]
        assert message["Injected"] is None
        assert "Alert body" in message.get_content()


@pytest.mark.parametrize("error", [TimeoutError("secret"), smtplib.SMTPAuthenticationError(535,b"secret"), smtplib.SMTPException("secret")])
def test_delivery_errors_do_not_expose_smtp_response(error):
    assert "secret" not in safe_delivery_error(error)


@pytest.mark.parametrize("role,found,expected", [("Viewer", True,403),("Admin",False,404)])
def test_email_api_rejects_viewer_and_cross_workspace_channel(role, found, expected):
    member_result = Mock()
    member_result.scalar_one_or_none.return_value = SimpleNamespace(role=role)
    channel_result = Mock()
    channel_result.scalar_one_or_none.return_value = None
    db = Mock(execute=AsyncMock(side_effect=[member_result, channel_result]))
    async def user():
        return SimpleNamespace(user_id=1)
    async def database():
        yield db
    app.dependency_overrides[get_current_user] = user
    app.dependency_overrides[get_db] = database
    try:
        response = TestClient(app).post("/api/v1/orgs/10/email-channels/999/test")
        assert response.status_code == expected
        if found:
            assert db.execute.await_count == 1
        else:
            stmt = str(db.execute.call_args.args[0])
            assert "email_channels.org_id" in stmt and "email_channels.channel_id" in stmt
    finally:
        app.dependency_overrides.clear()


@pytest.mark.parametrize("error,expected", [(None,"SENT"),(TimeoutError(),"FAILED"),(smtplib.SMTPAuthenticationError(535,b"secret"),"FAILED")])
def test_worker_records_email_result_without_changing_fcm(error, expected):
    now = datetime.now(UTC).replace(tzinfo=None)
    delivery = EmailDelivery(delivery_id=1, alert_id=1, channel_id=1, attempts=2, status="FAILED")
    alert = SimpleNamespace(message="Cảnh báo RAM\nRAM 95%", message_en="RAM alert\nRAM 95%", notification_status="SENT")
    channel = SimpleNamespace(config_encrypted="ciphertext")
    results = [Mock(all=Mock(return_value=[])), Mock(all=Mock(return_value=[(delivery,channel,alert)]))]
    db = Mock(execute=AsyncMock(side_effect=results), commit=AsyncMock())
    context = MagicMock()
    context.__aenter__ = AsyncMock(return_value=db)
    context.__aexit__ = AsyncMock(return_value=None)
    with patch.object(email_notifications,"SessionLocal",return_value=context), patch.object(email_notifications,"decrypt_config",return_value=config()), patch.object(email_notifications,"send_email",side_effect=error) as sender:
        asyncio.run(email_notifications.send_pending_emails())
    assert delivery.status == expected and delivery.attempts == 3
    assert alert.notification_status == "SENT"
    assert delivery.last_attempt_at >= now
    sender.assert_called_once()
    retry_query = str(db.execute.call_args.args[0])
    assert "email_deliveries.attempts <" in retry_query
    assert "email_deliveries.status !=" in retry_query


def test_default_offline_uses_device_setting_and_missing_temperature_is_not_failure():
    now = datetime.now(UTC).replace(tzinfo=None)
    device = SimpleNamespace(archived_at=None,is_monitoring=True,last_heartbeat=now-timedelta(minutes=3),offline_threshold_minutes=2,static_info={})
    rule = SimpleNamespace(metric_type="OFFLINE",default_key="OFFLINE",condition=">",threshold=5)
    assert rule_observation(rule,device,None,now)[0] is True
    device.last_heartbeat=now
    rule.metric_type="CPU_TEMP"
    assert rule_observation(rule,device,None,now) == (None,None)
