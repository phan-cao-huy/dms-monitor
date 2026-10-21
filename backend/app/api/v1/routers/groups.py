from collections.abc import Awaitable
from typing import Annotated, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.organization import DeviceGroup
from app.schemas.organization import (
    DeviceGroupOut,
    DeviceGroupUpdate,
    GroupMemberAssign,
    GroupMemberOut,
)
from app.services.organizations import (
    OrganizationError,
    assign_group_member,
    delete_group,
    get_group,
    list_group_members,
    remove_group_member,
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


@router.get(
    "/{group_id}/members",
    response_model=list[GroupMemberOut],
    summary="Danh sách thành viên được cấp quyền trong cụm",
)
async def get_group_members_endpoint(
    group_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[dict]:
    """Lấy danh sách thành viên có quyền truy cập cụm này."""
    return await _run(list_group_members(db, group_id, user.user_id))


@router.post(
    "/{group_id}/members",
    response_model=GroupMemberOut,
    status_code=status.HTTP_201_CREATED,
    summary="Cấp quyền thành viên trong cụm",
)
async def post_group_member_endpoint(
    group_id: int,
    payload: GroupMemberAssign,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict:
    """Cấp quyền Manager (quản lý) hoặc Viewer (người xem) cho thành viên trong cụm này."""
    return await _run(
        assign_group_member(db, group_id, user.user_id, payload.user_id, payload.role)
    )


@router.delete(
    "/{group_id}/members/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Thu hồi quyền thành viên khỏi cụm",
)
async def delete_group_member_endpoint(
    group_id: int,
    user_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Xóa quyền truy cập cụm thiết bị của người dùng."""
    await _run(remove_group_member(db, group_id, user.user_id, user_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)
