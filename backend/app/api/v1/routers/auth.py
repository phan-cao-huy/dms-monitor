from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.schemas.auth import (
    FcmTokenIn,
    LoginIn,
    RefreshIn,
    RegisterIn,
    TokenOut,
    UserOut,
)
from app.services.auth import (
    authenticate_user,
    issue_tokens,
    register_user,
    revoke_refresh_token,
    rotate_refresh_token,
    save_fcm_token,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.get(
    "/me",
    response_model=UserOut,
    summary="Lấy tài khoản hiện tại",
)
async def get_me(
    user: Annotated[User, Depends(get_current_user)],
) -> User:
    """Lấy thông tin tài khoản hiện tại từ access token."""
    return user


@router.post(
    "/register",
    response_model=UserOut,
    status_code=status.HTTP_201_CREATED,
    summary="Đăng ký tài khoản",
)
async def register(
    payload: RegisterIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    """Tạo tài khoản mới; trả HTTP 409 nếu username hoặc email đã được sử dụng."""
    try:
        return await register_user(db, payload)
    except IntegrityError as exc:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username or email is already registered",
        ) from exc


@router.post(
    "/login",
    response_model=TokenOut,
    summary="Đăng nhập",
)
async def login(
    payload: LoginIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> TokenOut:
    """Xác thực bằng username hoặc email và trả access token cùng refresh token."""
    user = await authenticate_user(db, payload.identifier, payload.password)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username/email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    access_token, refresh_token = await issue_tokens(db, user.user_id)
    return TokenOut(access_token=access_token, refresh_token=refresh_token)


@router.post(
    "/refresh",
    response_model=TokenOut,
    summary="Làm mới token",
)
async def refresh(
    payload: RefreshIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> TokenOut:
    """Đổi refresh token hiện tại lấy cặp token mới; token cũ bị thu hồi."""
    tokens = await rotate_refresh_token(db, payload.refresh_token)
    if tokens is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )
    _, access_token, refresh_token = tokens
    return TokenOut(access_token=access_token, refresh_token=refresh_token)


@router.post(
    "/logout",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Đăng xuất",
)
async def logout(
    payload: RefreshIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> Response:
    """Thu hồi refresh token để phiên đăng nhập đó không thể được làm mới."""
    await revoke_refresh_token(db, payload.refresh_token)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/fcm-token",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Đăng ký FCM token",
)
async def register_fcm_token(
    payload: FcmTokenIn,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Lưu hoặc cập nhật FCM token cho thiết bị của tài khoản đang đăng nhập."""
    await save_fcm_token(
        db,
        user.user_id,
        payload.fcm_token,
        payload.device_label,
        payload.app_version,
    )
    return Response(status_code=status.HTTP_204_NO_CONTENT)
