from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import decode_access_token
from app.db.session import get_db
from app.models.auth import User
from app.models.device import Device
from app.services.devices import DeviceError, verify_device_credentials

bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(bearer_scheme)
    ],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired access token",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if credentials is None:
        raise unauthorized
    try:
        user_id = decode_access_token(credentials.credentials)
    except ValueError:
        raise unauthorized from None

    user = await db.get(User, user_id)
    if user is None:
        raise unauthorized
    return user


async def get_optional_agent_device(
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> Device | None:
    token = request.headers.get("X-Device-Token")
    if not token:
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()
    if not token:
        token = request.query_params.get("secret_token")

    if not token:
        return None

    device_id_str = request.query_params.get("device_id")
    device_id = int(device_id_str) if device_id_str and device_id_str.isdigit() else None

    try:
        return await verify_device_credentials(db, token, device_id)
    except DeviceError:
        return None


async def get_current_agent_device(
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> Device:
    device = await get_optional_agent_device(request, db)
    if device is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing device credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return device
