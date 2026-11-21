"""Password recovery challenges. Serialize issue, verification and change per account."""
import asyncio
from datetime import UTC, datetime, timedelta
import hmac
import logging
import secrets

from sqlalchemy import func, select, text, update

from app.core.config import settings
from app.core.security import hash_password
from app.db.session import SessionLocal
from app.models.auth import AuthToken, User
from app.services.email_channels import send_email

logger = logging.getLogger(__name__)


def mail_config():
    if not (settings.RESET_SMTP_HOST and settings.RESET_SMTP_USERNAME and settings.RESET_SMTP_PASSWORD):
        return None
    return dict(host=settings.RESET_SMTP_HOST, port=settings.RESET_SMTP_PORT,
                username=settings.RESET_SMTP_USERNAME, password=settings.RESET_SMTP_PASSWORD.get_secret_value(),
                from_email=settings.RESET_SMTP_FROM or settings.RESET_SMTP_USERNAME)


def digest(uid, nonce, code):
    message = f"dms-password-reset:{uid}:{nonce}:{code}".encode()
    return hmac.digest(settings.JWT_SECRET_KEY.encode(), message, "sha256").hex()


async def locked_user(db, email):
    return await db.scalar(select(User).where(func.lower(User.email) == email.strip().lower())
                           .with_for_update().execution_options(populate_existing=True))


async def issue_code(db, email):
    user = await locked_user(db, email)
    if user is None:
        return None
    now = datetime.now(UTC).replace(tzinfo=None)
    previous = (await db.execute(text("SELECT * FROM password_recovery WHERE user_id=:uid"),
                                  {"uid": user.user_id})).mappings().first()
    start, deliveries = now, 0
    if previous:
        if previous["sent_at"] > now - timedelta(minutes=1):
            return None
        if previous["window_start"] > now - timedelta(hours=1):
            start, deliveries = previous["window_start"], previous["deliveries"]
            if deliveries >= 5:
                return None
    code = f"{secrets.randbelow(1_000_000):06d}"
    nonce = secrets.token_hex(16)
    await db.execute(text("""
        INSERT INTO password_recovery(user_id,digest,nonce,sent_at,window_start,deliveries)
        VALUES (:uid,:digest,:nonce,:now,:start,:deliveries)
        ON CONFLICT (user_id) DO UPDATE SET digest=excluded.digest,nonce=excluded.nonce,
          sent_at=excluded.sent_at,window_start=excluded.window_start,deliveries=excluded.deliveries,attempts=0
    """),dict(uid=user.user_id,digest=digest(user.user_id,nonce,code),nonce=nonce,now=now,start=start,deliveries=deliveries+1))
    await db.commit()
    return user.email, code


async def deliver_code(email, config):
    try:
        async with SessionLocal() as db:
            issued = await issue_code(db,email)
        if issued:
            recipient, code = issued
            await asyncio.to_thread(send_email, dict(config,recipients=[recipient]),
                                    "DMS Monitor – mã đặt lại mật khẩu",
                                    f"Mã đặt lại mật khẩu DMS của bạn là {code}. Mã có hiệu lực 15 phút. Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này.")
    except Exception as exc:
        logger.error("Password recovery delivery failed: %s",type(exc).__name__)


async def replace_password(db,user,new_password):
    user.password_version += 1
    user.password_hash = hash_password(new_password)
    now = datetime.now(UTC).replace(tzinfo=None)
    user.updated_at = now
    await db.execute(update(AuthToken).where(AuthToken.user_id==user.user_id,AuthToken.used_at.is_(None),
                        AuthToken.token_type.in_(["PASSWORD_RESET","REFRESH"])).values(used_at=now))
    await db.execute(text("DELETE FROM password_recovery WHERE user_id=:uid"),{"uid":user.user_id})
    await db.commit()


async def redeem_code(db,email,code,new_password):
    user = await locked_user(db,email)
    if user is None:
        return False
    args = {"uid":user.user_id}
    row = (await db.execute(text("SELECT * FROM password_recovery WHERE user_id=:uid"),args)).mappings().first()
    if not row or row["attempts"]>=5 or row["sent_at"]<=datetime.now(UTC).replace(tzinfo=None)-timedelta(minutes=15):
        return False
    if not hmac.compare_digest(row["digest"],digest(user.user_id,row["nonce"],code)):
        await db.execute(text("UPDATE password_recovery SET attempts=attempts+1 WHERE user_id=:uid"),args)
        await db.commit()
        return False
    user.failed_login_count=0
    user.locked_until=None
    await replace_password(db,user,new_password)
    return True
