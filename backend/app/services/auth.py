import logging
import secrets
from datetime import UTC, datetime, timedelta

from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from sqlalchemy import func, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings

from app.core.security import (
    create_access_token,
    create_refresh_token,
    hash_password,
    hash_refresh_token,
    verify_password,
)
from app.models.auth import AuthToken, User
from app.schemas.auth import RegisterIn

logger = logging.getLogger(__name__)


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


def _new_refresh_token(
    user_id: int,
    token: str,
    *,
    device_info: str | None = None,
    fcm_token: str | None = None,
) -> AuthToken:
    return AuthToken(
        user_id=user_id,
        token_type="REFRESH",
        token_hash=hash_refresh_token(token),
        expires_at=_utc_naive() + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS),
        device_info=device_info,
        fcm_token=fcm_token,
    )


async def register_user(db: AsyncSession, payload: RegisterIn) -> User:
    from app.models.organization import Organization, OrganizationMember

    user = User(
        username=payload.username.lower(),
        email=str(payload.email).lower(),
        password_hash=hash_password(payload.password),
        full_name=payload.full_name,
    )
    db.add(user)
    await db.flush()

    # Tu dong tao to chuc ca nhan mac dinh cho tai khoan moi (User la Owner)
    display_name = payload.full_name or payload.username
    default_org = Organization(
        org_name=f"Tổ chức của {display_name}",
        description=f"Không gian làm việc cá nhân của {display_name}",
        owner_id=user.user_id,
    )
    db.add(default_org)
    await db.flush()

    member = OrganizationMember(
        org_id=default_org.org_id,
        user_id=user.user_id,
        role="Owner",
    )
    db.add(member)

    await db.commit()
    await db.refresh(user)
    return user


async def authenticate_user(
    db: AsyncSession, identifier: str, password: str
) -> User | None:
    normalized = identifier.strip().lower()
    result = await db.execute(
        select(User).where(
            or_(func.lower(User.username) == normalized, func.lower(User.email) == normalized)
        ).with_for_update()
    )
    user = result.scalar_one_or_none()
    if (
        user is None
        or (user.locked_until is not None and user.locked_until > _utc_naive())
        or not verify_password(password, user.password_hash)
    ):
        return None
    user.last_login_at = _utc_naive()
    return user


async def issue_tokens(
    db: AsyncSession, user: User
) -> tuple[str, str]:
    refresh_token = create_refresh_token()
    db.add(_new_refresh_token(user.user_id, refresh_token))
    await db.commit()
    return create_access_token(user.user_id,user.password_version), refresh_token


async def rotate_refresh_token(db: AsyncSession, token: str) -> tuple[int, str, str] | None:
    token_hash = hash_refresh_token(token)
    uid = select(AuthToken.user_id).where(AuthToken.token_hash==token_hash, AuthToken.token_type=="REFRESH").scalar_subquery()
    user = await db.scalar(select(User).where(User.user_id==uid).with_for_update())
    if user is None:
        return None
    result = await db.execute(
        select(AuthToken)
        .where(
            AuthToken.token_hash == token_hash,
            AuthToken.token_type == "REFRESH",
        )
        .with_for_update()
    )
    stored_token = result.scalar_one_or_none()
    now = _utc_naive()
    if (
        stored_token is None
        or stored_token.used_at is not None
        or stored_token.expires_at <= now
    ):
        return None


    new_token = create_refresh_token()
    stored_token.used_at = now
    db.add(
        _new_refresh_token(
            user.user_id,
            new_token,
            device_info=stored_token.device_info,
            fcm_token=stored_token.fcm_token,
        )
    )
    await db.commit()
    return user.user_id, create_access_token(user.user_id,user.password_version), new_token


async def revoke_refresh_token(db: AsyncSession, token: str) -> None:
    await db.execute(
        update(AuthToken)
        .where(
            AuthToken.token_hash == hash_refresh_token(token),
            AuthToken.token_type == "REFRESH",
            AuthToken.used_at.is_(None),
        )
        .values(used_at=_utc_naive())
    )
    await db.commit()


async def change_user_password(
    db: AsyncSession, user: User, old_password: str, new_password: str
) -> bool:
    from app.services.password_recovery import locked_user, replace_password
    user = await locked_user(db,user.email)
    if user is None or not verify_password(old_password, user.password_hash):
        return False
    await replace_password(db,user,new_password)
    return True


async def save_fcm_token(
    db: AsyncSession,
    user_id: int,
    refresh_token: str,
    fcm_token: str,
    device_info: str | None,
) -> bool:
    now = _utc_naive()
    result = await db.execute(
        select(AuthToken)
        .where(
            AuthToken.user_id == user_id,
            AuthToken.token_hash == hash_refresh_token(refresh_token),
            AuthToken.token_type == "REFRESH",
            AuthToken.used_at.is_(None),
            AuthToken.expires_at > now,
        )
        .with_for_update()
    )
    stored_token = result.scalar_one_or_none()
    if stored_token is None:
        return False
    stored_token.fcm_token = fcm_token
    stored_token.device_info = device_info
    await db.commit()
    return True


async def authenticate_google_token(
    db: AsyncSession, credential: str
) -> User | None:
    try:
        idinfo = id_token.verify_oauth2_token(
            credential,
            google_requests.Request(),
            settings.GOOGLE_CLIENT_ID,
        )
    except Exception as exc:
        logger.warning(f"Google token verification failed: {exc}")
        return None

    email = idinfo.get("email")
    if not email or idinfo.get("email_verified") is not True:
        return None

    email = email.strip().lower()
    full_name = idinfo.get("name")
    avatar_url = idinfo.get("picture")

    result = await db.execute(
        select(User).where(func.lower(User.email) == email)
    )
    user = result.scalar_one_or_none()

    if user is not None:
        if user.locked_until and user.locked_until > _utc_naive():
            return None
        if avatar_url and not user.avatar_url:
            user.avatar_url = avatar_url
        user.is_email_verified = True
        user.last_login_at = _utc_naive()
        user.failed_login_count = 0
        user.locked_until = None
        await db.commit()
        return user

    from app.models.organization import Organization, OrganizationMember

    base_username = email.split("@")[0].lower()
    clean_username = "".join(c for c in base_username if c.isalnum() or c in "._-")[:40] or "user"
    candidate_username = clean_username
    counter = 1
    while True:
        res = await db.execute(
            select(User.user_id).where(func.lower(User.username) == candidate_username)
        )
        if res.scalar_one_or_none() is None:
            break
        candidate_username = f"{clean_username[:35]}{counter}"
        counter += 1

    user = User(
        username=candidate_username,
        email=email,
        password_hash=hash_password(secrets.token_urlsafe(32)),
        full_name=full_name or candidate_username,
        avatar_url=avatar_url,
        is_email_verified=True,
    )
    db.add(user)
    await db.flush()

    display_name = full_name or candidate_username
    default_org = Organization(
        org_name=f"Tổ chức của {display_name}",
        description=f"Không gian làm việc cá nhân của {display_name}",
        owner_id=user.user_id,
    )
    db.add(default_org)
    await db.flush()

    member = OrganizationMember(
        org_id=default_org.org_id,
        user_id=user.user_id,
        role="Owner",
    )
    db.add(member)

    await db.commit()
    await db.refresh(user)
    return user
