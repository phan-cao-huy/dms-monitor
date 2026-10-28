"""Independent email delivery ledger; FCM status is intentionally unchanged."""
import asyncio
import smtplib
from datetime import UTC, datetime, timedelta

from cryptography.fernet import InvalidToken
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert

from app.db.session import SessionLocal
from app.models.alert import AlertHistory
from app.models.device import Device
from app.models.email_channel import EmailChannel, EmailDelivery
from app.models.organization import Organization
from app.services.email_channels import decrypt_config, safe_delivery_error, send_email


async def send_pending_emails():
    now = datetime.now(UTC).replace(tzinfo=None)
    async with SessionLocal() as db:
        pairs = (await db.execute(
            select(AlertHistory.alert_id, EmailChannel.channel_id)
            .join(Device, Device.device_id == AlertHistory.device_id)
            .join(EmailChannel, EmailChannel.org_id == Device.org_id)
            .join(Organization, Organization.org_id == Device.org_id)
            .where(EmailChannel.enabled.is_(True), Organization.deleted_at.is_(None),
                   Device.archived_at.is_(None), Device.is_monitoring.is_(True),
                   AlertHistory.resolved_at.is_(None), AlertHistory.created_at >= now - timedelta(hours=24),
                   AlertHistory.created_at >= EmailChannel.created_at,
                   ~select(EmailDelivery.delivery_id).where(EmailDelivery.alert_id == AlertHistory.alert_id, EmailDelivery.channel_id == EmailChannel.channel_id).exists())
            .order_by(AlertHistory.alert_id).limit(500)
        )).all()
        for alert_id, channel_id in pairs:
            await db.execute(insert(EmailDelivery).values(alert_id=alert_id, channel_id=channel_id,
                             status="PENDING", attempts=0).on_conflict_do_nothing())
        await db.commit()

        rows = (await db.execute(select(EmailDelivery, EmailChannel, AlertHistory)
            .join(EmailChannel, EmailChannel.channel_id == EmailDelivery.channel_id)
            .join(AlertHistory, AlertHistory.alert_id == EmailDelivery.alert_id)
            .join(Device, Device.device_id == AlertHistory.device_id)
            .join(Organization, Organization.org_id == Device.org_id)
            .where(EmailChannel.enabled.is_(True), EmailDelivery.status != "SENT", EmailDelivery.attempts < 3,
                   Device.archived_at.is_(None), Device.is_monitoring.is_(True), Organization.deleted_at.is_(None),
                   AlertHistory.resolved_at.is_(None), AlertHistory.created_at >= now - timedelta(hours=24))
            .order_by(EmailDelivery.delivery_id).limit(100))).all()
        for delivery, channel, alert in rows:
            if delivery.last_attempt_at and now - delivery.last_attempt_at < timedelta(minutes=1):
                continue
            delivery.attempts += 1
            delivery.last_attempt_at = now
            delivery.status = "FAILED"
            await db.commit()  # reserve attempt before network I/O; scheduler has a single DB leader
            try:
                config = decrypt_config(channel)
                text = (alert.message_en or alert.message) if config["language"] == "en" else alert.message
                subject, _, body = text.partition("\n")
                await asyncio.to_thread(send_email, config, subject, body or text)
                delivery.status, delivery.sent_at, delivery.last_error = "SENT", datetime.now(UTC).replace(tzinfo=None), None
            except (smtplib.SMTPException, OSError, InvalidToken, HTTPException, ValueError) as exc:
                delivery.last_error = safe_delivery_error(exc)
            await db.commit()
