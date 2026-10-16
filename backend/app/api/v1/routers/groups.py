from collections.abc import Awaitable
from typing import Annotated, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.organization import DeviceGroup
from app.schemas.organization import DeviceGroupOut, DeviceGroupUpdate
from app.services.organizations import (
    OrganizationError,
    delete_group,
    get_group,
    update_group,
)

router = APIRouter(prefix="/groups", tags=["groups"])
Result = TypeVar("Result")


async def _run(operation: Awaitable[Result]) -> Result:
    try:
        return await operation
    except OrganizationError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail=exc.detail,
        ) from exc


@router.get(
    "/{group_id}",
    response_model=DeviceGroupOut,
    summary="Chi tiết cụm thiết bị",
)
async def get_group_endpoint(
    group_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> DeviceGroup:
    """Lấy thông tin chi tiết của một cụm thiết bị."""
    return await _run(get_group(db, group_id, user.user_id))


@router.patch(
    "/{group_id}",
    response_model=DeviceGroupOut,
    summary="Cập nhật cụm thiết bị",
)
async def patch_group_endpoint(
    group_id: int,
    payload: DeviceGroupUpdate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> DeviceGroup:
    """Cập nhật tên hoặc mô tả cụm thiết bị; yêu cầu quyền Owner hoặc Admin."""
    return await _run(update_group(db, group_id, user.user_id, payload))


@router.delete(
    "/{group_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Xóa cụm thiết bị",
)
async def delete_group_endpoint(
    group_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Xóa cụm thiết bị; các thiết bị trong cụm sẽ chuyển về không thuộc cụm nào (SET NULL)."""
    await _run(delete_group(db, group_id, user.user_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)
