"""Workspace-scoped SMTP channels. Secrets are encrypted and never returned."""

import json
import smtplib
import ssl
from email.message import EmailMessage

from cryptography.fernet import Fernet
from fastapi import HTTPException

from app.core.config import settings


def cipher():
    if not settings.NOTIFICATION_ENCRYPTION_KEY:
        raise HTTPException(503, "Email configuration requires NOTIFICATION_ENCRYPTION_KEY on server")
    try:
        return Fernet(settings.NOTIFICATION_ENCRYPTION_KEY.encode())
    except ValueError:
        raise HTTPException(503, "Invalid notification encryption key") from None


def encrypt_config(config):
    return cipher().encrypt(json.dumps(config).encode()).decode()


def decrypt_config(channel):
    return json.loads(cipher().decrypt(channel.config_encrypted.encode()))


def channel_out(channel):
    config = decrypt_config(channel)
    config.pop("password", None)
    return {"channel_id": channel.channel_id, "name": channel.name,
            "enabled": channel.enabled, "config": config, "password_set": True}


def send_email(config, subject, body):
    message = EmailMessage()
    message["Subject"] = subject.replace("\r", " ").replace("\n", " ")[:180]
    message["From"] = config["from_email"]
    message["To"] = ", ".join(config["recipients"])
    message.set_content(body)
    context = ssl.create_default_context()
    if config["port"] == 465:
        client = smtplib.SMTP_SSL(config["host"], 465, timeout=10, context=context)
    else:
        client = smtplib.SMTP(config["host"], 587, timeout=10)
    with client:
        if config["port"] == 587:
            client.ehlo()
            client.starttls(context=context)
            client.ehlo()
        client.login(config["username"], config["password"])
        refused = client.send_message(message)
        if refused:
            raise smtplib.SMTPException("Some recipients were rejected")


def safe_delivery_error(exc):
    # SMTP exceptions may contain credentials, addresses and server responses.
    if isinstance(exc, smtplib.SMTPAuthenticationError):
        return "SMTP_AUTH_FAILED"
    if isinstance(exc, (TimeoutError, OSError)):
        return "SMTP_CONNECTION_FAILED"
    return "SMTP_DELIVERY_FAILED"
