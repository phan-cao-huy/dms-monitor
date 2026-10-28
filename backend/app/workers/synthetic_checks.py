import asyncio
import smtplib
from datetime import UTC, datetime, timedelta

from cryptography.fernet import InvalidToken
from fastapi import HTTPException
from sqlalchemy import or_, select, text
from sqlalchemy.dialects.postgresql import insert

from app.db.session import SessionLocal
from app.models.email_channel import EmailChannel
from app.models.monitoring import CheckEmailDelivery, CheckIncident, SyntheticCheck
from app.models.organization import Organization
from app.services.email_channels import decrypt_config, safe_delivery_error, send_email
from app.services.synthetic_checks import due, run_check


async def run_synthetic_checks():
    now = datetime.now(UTC).replace(tzinfo=None)
    async with SessionLocal() as db:
        ids = (await db.execute(select(SyntheticCheck.check_id).join(Organization, Organization.org_id == SyntheticCheck.org_id)
            .where(SyntheticCheck.enabled.is_(True), Organization.deleted_at.is_(None),
                   or_(SyntheticCheck.last_checked_at.is_(None), SyntheticCheck.last_checked_at + SyntheticCheck.interval_seconds * text("INTERVAL '1 second'") <= now))
            .order_by(SyntheticCheck.last_checked_at.asc().nullsfirst()).limit(100))).scalars().all()
    semaphore = asyncio.Semaphore(5)

    async def execute(check_id):
        async with semaphore, SessionLocal() as db:
            check = (await db.execute(select(SyntheticCheck).where(SyntheticCheck.check_id == check_id, SyntheticCheck.enabled.is_(True))
                                      .with_for_update(skip_locked=True))).scalar_one_or_none()
            if check and due(check, datetime.now(UTC).replace(tzinfo=None)):
                await run_check(db, check)
    await asyncio.gather(*(execute(check_id) for check_id in ids))


async def send_check_emails():
    now = datetime.now(UTC).replace(tzinfo=None)
    async with SessionLocal() as db:
        pairs = (await db.execute(select(CheckIncident.incident_id, EmailChannel.channel_id)
            .join(SyntheticCheck, SyntheticCheck.check_id == CheckIncident.check_id)
            .join(Organization, Organization.org_id == SyntheticCheck.org_id)
            .join(EmailChannel, EmailChannel.org_id == SyntheticCheck.org_id)
            .where(Organization.deleted_at.is_(None), SyntheticCheck.enabled.is_(True), EmailChannel.enabled.is_(True),
                   CheckIncident.resolved_at.is_(None), CheckIncident.opened_at >= now-timedelta(hours=24),
                   CheckIncident.opened_at >= EmailChannel.created_at,
                   ~select(CheckEmailDelivery.delivery_id).where(CheckEmailDelivery.incident_id == CheckIncident.incident_id,
                                                                CheckEmailDelivery.channel_id == EmailChannel.channel_id).exists())
            .limit(500))).all()
        for incident_id, channel_id in pairs:
            await db.execute(insert(CheckEmailDelivery).values(incident_id=incident_id, channel_id=channel_id,
                             status="PENDING", attempts=0).on_conflict_do_nothing())
        await db.commit()
        rows = (await db.execute(select(CheckEmailDelivery, EmailChannel, CheckIncident, SyntheticCheck)
            .join(EmailChannel, EmailChannel.channel_id == CheckEmailDelivery.channel_id)
            .join(CheckIncident, CheckIncident.incident_id == CheckEmailDelivery.incident_id)
            .join(SyntheticCheck, SyntheticCheck.check_id == CheckIncident.check_id)
            .join(Organization, Organization.org_id == SyntheticCheck.org_id)
            .where(CheckEmailDelivery.status != "SENT", CheckEmailDelivery.attempts < 3,
                   EmailChannel.enabled.is_(True), SyntheticCheck.enabled.is_(True), Organization.deleted_at.is_(None),
                   CheckIncident.resolved_at.is_(None), CheckIncident.opened_at >= now-timedelta(hours=24))
            .order_by(CheckEmailDelivery.delivery_id).limit(100))).all()
        for delivery, channel, incident, check in rows:
            if delivery.last_attempt_at and now-delivery.last_attempt_at < timedelta(minutes=1):
                continue
            delivery.attempts += 1
            delivery.last_attempt_at, delivery.status = now, "FAILED"
            await db.commit()
            try:
                config = decrypt_config(channel)
                en = config["language"] == "en"
                subject = ("Service alert · " if en else "Cảnh báo dịch vụ · ") + check.name
                body = ((f"{check.name}: {check.type}\nCheck failed {check.consecutive_failures} consecutive times.\n" if en else
                         f"{check.name}: {check.type}\nKiểm tra thất bại {check.consecutive_failures} lần liên tiếp.\n") +
                        (("TLS certificate expires in " if en else "Chứng chỉ HTTPS còn ") + incident.detail.split(":",1)[1] + (" days." if en else " ngày.")
                         if incident.detail.startswith("SSL_DAYS:") else describe_result(incident.detail,en)) +
                        ("\nOpen DMS → Service checks for details." if en else "\nMở DMS → Kiểm tra dịch vụ để xem chi tiết."))
                await asyncio.to_thread(send_email, config, subject, body)
                delivery.status, delivery.sent_at, delivery.last_error = "SENT", datetime.now(UTC).replace(tzinfo=None), None
            except (smtplib.SMTPException, OSError, InvalidToken, HTTPException, ValueError) as exc:
                delivery.last_error = safe_delivery_error(exc)
            await db.commit()


def describe_result(code, en=False):
    if code.startswith("HTTP_"):
        return ("The website/API returned HTTP " if en else "Website/API trả về HTTP ") + code[5:] + "."
    messages={
        "TIMEOUT": ("Không nhận phản hồi trong thời gian chờ đã đặt.", "No response within the configured timeout."),
        "PING_FAILED": ("Máy chủ không phản hồi Ping từ server DMS.", "The host did not respond to Ping from DMS."),
        "CONNECTION_FAILED": ("Không kết nối được dịch vụ hoặc phản hồi không hợp lệ.", "Cannot connect to the service or its response is invalid."),
        "TLS_VALIDATION_FAILED": ("Chứng chỉ HTTPS không hợp lệ hoặc đã hết hạn.", "The HTTPS certificate is invalid or expired."),
        "TARGET_BLOCKED": ("Địa chỉ đích bị chặn bởi chính sách mạng của server DMS.", "The target is blocked by the DMS server network policy."),
        "TOO_MANY_REDIRECTS": ("Website chuyển hướng quá 3 lần.", "The website redirected more than 3 times."),
        "PING_UNAVAILABLE": ("Server DMS chưa có công cụ Ping.", "Ping is unavailable on the DMS server."),
    }
    return messages.get(code,("Không kiểm tra được dịch vụ.","The service check failed."))[1 if en else 0]
