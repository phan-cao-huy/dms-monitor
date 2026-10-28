import asyncio
import smtplib
from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.email_channel import EmailChannel, EmailDelivery
from app.schemas.email_channel import EmailChannelWrite
from app.services.alerts import AlertError, _get_org_member
from app.services.email_channels import (
    channel_out,
    decrypt_config,
    encrypt_config,
    safe_delivery_error,
    send_email,
)

router = APIRouter(prefix="/orgs/{org_id}/email-channels", tags=["email-channels"])
DB = Annotated[AsyncSession, Depends(get_db)]
CurrentUser = Annotated[User, Depends(get_current_user)]


async def admin(db, org_id, user_id):
    try:
        member = await _get_org_member(db, org_id, user_id)
    except AlertError as exc:
        raise HTTPException(exc.status_code, exc.detail) from exc
    if member.role not in ("Owner", "Admin"):
        raise HTTPException(403, "Only Owner/Admin can manage email channels")


async def get_channel(db, org_id, channel_id, lock=False):
    stmt = select(EmailChannel).where(EmailChannel.org_id == org_id, EmailChannel.channel_id == channel_id)
    if lock:
        stmt = stmt.with_for_update()
    channel = (await db.execute(stmt)).scalar_one_or_none()
    if channel is None:
        raise HTTPException(404, "Email channel not found")
    return channel


@router.get("")
async def list_channels(org_id: int, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    channels = (await db.execute(select(EmailChannel).where(EmailChannel.org_id == org_id).order_by(EmailChannel.channel_id))).scalars().all()
    return [channel_out(c) for c in channels]


@router.post("", status_code=201)
async def create_channel(org_id: int, payload: EmailChannelWrite, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    if not payload.config.password:
        raise HTTPException(422, "SMTP password is required")
    channel = EmailChannel(org_id=org_id, name=payload.name, enabled=payload.enabled,
                           config_encrypted=encrypt_config(payload.config.model_dump(mode="json")))
    db.add(channel)
    await db.commit()
    await db.refresh(channel)
    return channel_out(channel)


@router.put("/{channel_id}")
async def update_channel(org_id: int, channel_id: int, payload: EmailChannelWrite, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    channel = await get_channel(db, org_id, channel_id, lock=True)
    config = payload.config.model_dump(mode="json")
    if not config["password"]:
        config["password"] = decrypt_config(channel)["password"]
    channel.config_encrypted = encrypt_config(config)
    channel.name, channel.enabled = payload.name, payload.enabled
    await db.commit()
    return channel_out(channel)


@router.delete("/{channel_id}", status_code=204)
async def delete_channel(org_id: int, channel_id: int, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    channel = await get_channel(db, org_id, channel_id, lock=True)
    await db.delete(channel)
    await db.commit()
    return Response(status_code=204)


@router.post("/{channel_id}/test")
async def test_channel(org_id: int, channel_id: int, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    channel = await get_channel(db, org_id, channel_id, lock=True)
    now = datetime.now(UTC).replace(tzinfo=None)
    if channel.last_test_at and now - channel.last_test_at < timedelta(seconds=60):
        raise HTTPException(429, "Wait 60 seconds before sending another test")
    config = decrypt_config(channel)
    channel.last_test_at = now
    await db.commit()
    en = config["language"] == "en"
    try:
        await asyncio.to_thread(send_email, config,
                                "DMS Monitor · Email test" if en else "DMS Monitor · Gửi thử email",
                                "Email notifications are configured correctly." if en else "Kênh email đã được cấu hình thành công. Đây là thông báo thử từ DMS Monitor.")
    except (smtplib.SMTPException, OSError) as exc:
        raise HTTPException(502, safe_delivery_error(exc)) from None
    return {"status": "SENT"}


@router.get("/{channel_id}/deliveries")
async def deliveries(org_id: int, channel_id: int, db: DB, user: CurrentUser):
    await admin(db, org_id, user.user_id)
    await get_channel(db, org_id, channel_id)
    rows = (await db.execute(select(EmailDelivery).where(EmailDelivery.channel_id == channel_id).order_by(EmailDelivery.delivery_id.desc()).limit(50))).scalars().all()
    return [{"alert_id": r.alert_id, "status": r.status, "attempts": r.attempts,
             "last_error": r.last_error, "sent_at": r.sent_at} for r in rows]
