from datetime import UTC, datetime, timedelta

from sqlalchemy import func, or_, select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import (
    create_access_token,
    create_refresh_token,
    hash_password,
    hash_refresh_token,
    verify_password,
)
from app.models.auth import RefreshToken, User, UserFcmToken
from app.schemas.auth import RegisterIn


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


def _new_refresh_token(user_id: int, token: str) -> RefreshToken:
    return RefreshToken(
        user_id=user_id,
        token_hash=hash_refresh_token(token),
        expires_at=_utc_naive() + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS),
    )


async def register_user(db: AsyncSession, payload: RegisterIn) -> User:
    user = User(
        username=payload.username.lower(),
        email=str(payload.email).lower(),
        password_hash=hash_password(payload.password),
        full_name=payload.full_name,
    )
    db.add(user)
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
        )
    )
    user = result.scalar_one_or_none()
    if user is None or not user.is_active or not verify_password(password, user.password_hash):
        return None
    user.last_login_at = _utc_naive()
    await db.commit()
    return user


async def issue_tokens(
    db: AsyncSession, user_id: int
) -> tuple[str, str]:
    refresh_token = create_refresh_token()
    db.add(_new_refresh_token(user_id, refresh_token))
    await db.commit()
    return create_access_token(user_id), refresh_token


async def rotate_refresh_token(db: AsyncSession, token: str) -> tuple[int, str, str] | None:
    token_hash = hash_refresh_token(token)
    result = await db.execute(
        select(RefreshToken)
        .where(RefreshToken.token_hash == token_hash)
        .with_for_update()
    )
    stored_token = result.scalar_one_or_none()
    now = _utc_naive()
    if (
        stored_token is None
        or stored_token.is_revoked
        or stored_token.expires_at <= now
    ):
        return None

    user = await db.get(User, stored_token.user_id)
    if user is None or not user.is_active:
        return None

    new_token = create_refresh_token()
    stored_token.is_revoked = True
    stored_token.last_used_at = now
    db.add(_new_refresh_token(user.user_id, new_token))
    await db.commit()
    return user.user_id, create_access_token(user.user_id), new_token


async def revoke_refresh_token(db: AsyncSession, token: str) -> None:
    await db.execute(
        update(RefreshToken)
        .where(
            RefreshToken.token_hash == hash_refresh_token(token),
            RefreshToken.is_revoked.is_(False),
        )
        .values(is_revoked=True, last_used_at=_utc_naive())
    )
    await db.commit()


async def save_fcm_token(
    db: AsyncSession, user_id: int, fcm_token: str, device_label: str | None,
    app_version: str | None
) -> None:
    now = _utc_naive()
    statement = insert(UserFcmToken).values(
        user_id=user_id,
        fcm_token=fcm_token,
        device_label=device_label,
        app_version=app_version,
        is_active=True,
        last_used_at=now,
    )
    await db.execute(
        statement.on_conflict_do_update(
            index_elements=[UserFcmToken.user_id, UserFcmToken.fcm_token],
            set_={
                "device_label": device_label,
                "app_version": app_version,
                "is_active": True,
                "last_used_at": now,
            },
        )
    )
    await db.commit()
