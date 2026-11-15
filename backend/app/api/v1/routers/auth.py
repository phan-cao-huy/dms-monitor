from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.schemas.auth import (
    ChangePasswordIn,
    FcmTokenIn,
    ForgotPasswordIn,
    GoogleLoginIn,
    LoginIn,
    RefreshIn,
    RegisterIn,
    ResetPasswordIn,
    TokenOut,
    UserDetailOut,
    UserOut,
)
from app.services.auth import (
    authenticate_google_token,
    authenticate_user,
    change_user_password,
    create_password_reset_code,
    issue_tokens,
    register_user,
    reset_password_with_code,
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


@router.get(
    "/me/detail",
    response_model=UserDetailOut,
    summary="Lấy thông tin chi tiết tài khoản hiện tại",
)
async def get_me_detail(
    user: Annotated[User, Depends(get_current_user)],
) -> User:
    """Lấy đầy đủ thông tin chi tiết tài khoản hiện tại từ access token."""
    return user


@router.get(
    "/users",
    response_model=list[UserDetailOut],
    summary="Lấy hồ sơ tài khoản hiện tại",
)
async def get_all_users(
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[User]:
    """Giữ tương thích dạng danh sách, chỉ trả hồ sơ của chính người dùng."""
    return [user]


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
    "/google",
    response_model=TokenOut,
    summary="Đăng nhập hoặc đăng ký nhanh bằng Google",
)
async def google_login(
    payload: GoogleLoginIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> TokenOut:
    """Xác thực ID Token từ Google OAuth, tự tạo tài khoản nếu chưa có và cấp JWT token."""
    user = await authenticate_google_token(db, payload.credential)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Xác thực tài khoản Google không thành công hoặc token không hợp lệ",
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
    """Gắn FCM token vào phiên refresh token đang đăng nhập."""
    saved = await save_fcm_token(
        db,
        user.user_id,
        payload.refresh_token,
        payload.fcm_token,
        payload.device_info,
    )
    if not saved:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/change-password",
    summary="Đổi mật khẩu tài khoản",
)
async def change_password(
    payload: ChangePasswordIn,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict[str, str]:
    """Đổi mật khẩu tài khoản khi đã đăng nhập."""
    success = await change_user_password(
        db, user, payload.old_password, payload.new_password
    )
    if not success:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Mật khẩu hiện tại không chính xác",
        )
    return {"message": "Đổi mật khẩu thành công"}


@router.post(
    "/forgot-password",
    summary="Yêu cầu mã đặt lại mật khẩu",
)
async def forgot_password(
    payload: ForgotPasswordIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict[str, str | None]:
    """Gửi yêu cầu quên mật khẩu, sinh mã xác thực OTP 6 số (hạn 15 phút)."""
    exists, code = await create_password_reset_code(db, str(payload.email))
    if not exists:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Không tìm thấy tài khoản với email này",
        )
    return {
        "message": "Mã xác thực đặt lại mật khẩu đã được tạo (hiệu lực 15 phút).",
        "reset_code": code,
    }


@router.post(
    "/reset-password",
    summary="Đặt lại mật khẩu mới bằng mã OTP",
)
async def reset_password(
    payload: ResetPasswordIn,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict[str, str]:
    """Đặt lại mật khẩu mới bằng mã xác thực 6 số."""
    success = await reset_password_with_code(
        db, str(payload.email), payload.reset_code, payload.new_password
    )
    if not success:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Mã xác thực không hợp lệ, đã hết hạn hoặc email không chính xác",
        )
    return {"message": "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập ngay."}


