from collections.abc import Awaitable
from typing import Annotated, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.organization import (
    DeviceGroup,
    OrganizationInvitation,
    OrganizationMember,
)
from app.schemas.organization import (
    DeviceGroupCreate,
    DeviceGroupOut,
    InvitationCreate,
    InvitationOut,
    JoinOrganizationIn,
    JoinOrganizationOut,
    MemberOut,
    MemberRoleUpdate,
    OrganizationCreate,
    OrganizationOut,
)
from app.services.organizations import (
    OrganizationError,
    create_group,
    create_invitation,
    create_organization,
    join_organization,
    list_groups,
    list_invitations,
    list_members,
    list_organizations,
    remove_member,
    update_member_role,
)

router = APIRouter(prefix="/orgs", tags=["organizations"])
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
    "",
    response_model=list[OrganizationOut],
    summary="Liệt kê tổ chức",
)
async def get_organizations(
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[dict]:
    """Trả về các tổ chức đang hoạt động mà tài khoản hiện tại là thành viên."""
    return await list_organizations(db, user.user_id)


@router.post(
    "",
    response_model=OrganizationOut,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo tổ chức",
)
async def post_organization(
    payload: OrganizationCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict:
    """Tạo tổ chức mới và tự thêm người tạo vào tổ chức với vai trò Owner."""
    return await create_organization(db, user.user_id, payload)


@router.post(
    "/join",
    response_model=JoinOrganizationOut,
    status_code=status.HTTP_200_OK,
    summary="Tham gia tổ chức",
)
async def join_org(
    payload: JoinOrganizationIn,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict:
    """Tham gia tổ chức bằng mã mời còn hiệu lực và chưa hết lượt sử dụng."""
    return await _run(join_organization(db, user.user_id, payload.invite_code))


@router.get(
    "/{org_id}/invitations",
    response_model=list[InvitationOut],
    summary="Liệt kê mã mời",
)
async def get_invitations(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[OrganizationInvitation]:
    """Liệt kê mã mời của tổ chức; chỉ Owner hoặc Admin được phép."""
    return await _run(list_invitations(db, org_id, user.user_id))


@router.post(
    "/{org_id}/invitations",
    response_model=InvitationOut,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo mã mời",
)
async def post_invitation(
    org_id: int,
    payload: InvitationCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> OrganizationInvitation:
    """Tạo mã mời Manager hoặc Viewer; giới hạn lượt dùng và hạn dùng là tùy chọn."""
    return await _run(create_invitation(db, org_id, user.user_id, payload))


@router.get(
    "/{org_id}/members",
    response_model=list[MemberOut],
    summary="Liệt kê thành viên",
)
async def get_members(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[dict]:
    """Liệt kê thành viên; chỉ thành viên của tổ chức mới xem được."""
    return await _run(list_members(db, org_id, user.user_id))


@router.patch(
    "/{org_id}/members/{user_id}",
    response_model=MemberOut,
    summary="Đổi vai trò thành viên",
)
async def patch_member(
    org_id: int,
    user_id: int,
    payload: MemberRoleUpdate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict:
    """Đổi vai trò thành viên; chỉ Owner được cấp hoặc thay đổi vai trò Admin."""
    member = await _run(
        update_member_role(db, org_id, user.user_id, user_id, payload)
    )
    result = await _run(list_members(db, org_id, user.user_id))
    return next(item for item in result if item["user_id"] == member.user_id)


@router.delete(
    "/{org_id}/members/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Xóa thành viên",
)
async def delete_member(
    org_id: int,
    user_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Xóa thành viên khỏi tổ chức; không thể xóa Owner."""
    await _run(remove_member(db, org_id, user.user_id, user_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get(
    "/{org_id}/groups",
    response_model=list[DeviceGroupOut],
    summary="Liệt kê cụm thiết bị",
)
async def get_groups(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[DeviceGroup]:
    """Liệt kê các cụm thiết bị đang hoạt động trong tổ chức."""
    return await _run(list_groups(db, org_id, user.user_id))


@router.post(
    "/{org_id}/groups",
    response_model=DeviceGroupOut,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo cụm thiết bị",
)
async def post_group(
    org_id: int,
    payload: DeviceGroupCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> DeviceGroup:
    """Tạo cụm thiết bị mới; chỉ Owner hoặc Admin được phép."""
    return await _run(create_group(db, org_id, user.user_id, payload))
