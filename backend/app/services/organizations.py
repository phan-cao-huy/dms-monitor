import secrets
import string
from datetime import UTC, datetime
from typing import NoReturn

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.auth import User
from app.models.organization import (
    DeviceGroup,
    Organization,
    OrganizationInvitation,
    OrganizationMember,
)
from app.schemas.organization import (
    DeviceGroupCreate,
    InvitationCreate,
    MemberRoleUpdate,
    OrganizationCreate,
)


class OrganizationError(Exception):
    def __init__(self, status_code: int, detail: str) -> None:
        self.status_code = status_code
        self.detail = detail
        super().__init__(detail)


def _fail(status_code: int, detail: str) -> NoReturn:
    raise OrganizationError(status_code, detail)


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


def _as_utc_naive(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is not None:
        return value.astimezone(UTC).replace(tzinfo=None)
    return value


async def _get_member(
    db: AsyncSession, org_id: int, user_id: int
) -> OrganizationMember:
    result = await db.execute(
        select(OrganizationMember)
        .join(Organization, Organization.org_id == OrganizationMember.org_id)
        .where(
            OrganizationMember.org_id == org_id,
            OrganizationMember.user_id == user_id,
            Organization.is_active.is_(True),
            Organization.deleted_at.is_(None),
        )
    )
    member = result.scalar_one_or_none()
    if member is None:
        _fail(404, "Organization not found")
    return member


async def _require_admin(
    db: AsyncSession, org_id: int, user_id: int
) -> OrganizationMember:
    member = await _get_member(db, org_id, user_id)
    if member.role not in {"Owner", "Admin"}:
        _fail(403, "Owner or Admin role required")
    return member


async def list_organizations(db: AsyncSession, user_id: int) -> list[dict]:
    result = await db.execute(
        select(Organization, OrganizationMember.role)
        .join(OrganizationMember, OrganizationMember.org_id == Organization.org_id)
        .where(
            OrganizationMember.user_id == user_id,
            Organization.is_active.is_(True),
            Organization.deleted_at.is_(None),
        )
        .order_by(Organization.org_name)
    )
    return [
        {
            "org_id": org.org_id,
            "org_name": org.org_name,
            "description": org.description,
            "logo_url": org.logo_url,
            "owner_id": org.owner_id,
            "role": role,
            "created_at": org.created_at,
        }
        for org, role in result.all()
    ]


async def create_organization(
    db: AsyncSession, user_id: int, payload: OrganizationCreate
) -> dict:
    organization = Organization(
        org_name=payload.org_name.strip(),
        description=payload.description,
        logo_url=payload.logo_url,
        owner_id=user_id,
    )
    db.add(organization)
    await db.flush()
    member = OrganizationMember(
        org_id=organization.org_id,
        user_id=user_id,
        role="Owner",
    )
    db.add(member)
    await db.commit()
    await db.refresh(organization)
    return {
        "org_id": organization.org_id,
        "org_name": organization.org_name,
        "description": organization.description,
        "logo_url": organization.logo_url,
        "owner_id": organization.owner_id,
        "role": "Owner",
        "created_at": organization.created_at,
    }


async def join_organization(
    db: AsyncSession, user_id: int, invite_code: str
) -> dict:
    result = await db.execute(
        select(OrganizationInvitation)
        .join(Organization, Organization.org_id == OrganizationInvitation.org_id)
        .where(
            OrganizationInvitation.invite_code == invite_code.upper(),
            OrganizationInvitation.is_active.is_(True),
            Organization.is_active.is_(True),
            Organization.deleted_at.is_(None),
        )
        .with_for_update()
    )
    invitation = result.scalar_one_or_none()
    now = _utc_naive()
    if (
        invitation is None
        or (_as_utc_naive(invitation.expires_at) is not None
            and _as_utc_naive(invitation.expires_at) <= now)
        or (invitation.max_uses is not None and invitation.use_count >= invitation.max_uses)
    ):
        _fail(404, "Invitation is invalid, expired, or fully used")

    existing = await db.execute(
        select(OrganizationMember.member_id).where(
            OrganizationMember.org_id == invitation.org_id,
            OrganizationMember.user_id == user_id,
        )
    )
    if existing.scalar_one_or_none() is not None:
        _fail(409, "You are already a member of this organization")

    member = OrganizationMember(
        org_id=invitation.org_id,
        user_id=user_id,
        role=invitation.default_role,
        invited_by=invitation.created_by,
        invite_code_used=invitation.invite_code,
    )
    invitation.use_count += 1
    db.add(member)
    await db.commit()
    await db.refresh(member)
    return {
        "org_id": member.org_id,
        "user_id": member.user_id,
        "role": member.role,
        "joined_at": member.joined_at,
    }


async def list_invitations(
    db: AsyncSession, org_id: int, user_id: int
) -> list[OrganizationInvitation]:
    await _require_admin(db, org_id, user_id)
    result = await db.execute(
        select(OrganizationInvitation)
        .where(OrganizationInvitation.org_id == org_id)
        .order_by(OrganizationInvitation.created_at.desc())
    )
    return list(result.scalars().all())


async def create_invitation(
    db: AsyncSession,
    org_id: int,
    user_id: int,
    payload: InvitationCreate,
) -> OrganizationInvitation:
    await _require_admin(db, org_id, user_id)
    expires_at = _as_utc_naive(payload.expires_at)
    if expires_at is not None and expires_at <= _utc_naive():
        _fail(422, "Invitation expiration must be in the future")

    alphabet = string.ascii_uppercase + string.digits
    invitation = OrganizationInvitation(
        org_id=org_id,
        invite_code="".join(secrets.choice(alphabet) for _ in range(6)),
        invite_token=secrets.token_urlsafe(48)[:64],
        default_role=payload.default_role,
        max_uses=payload.max_uses,
        expires_at=expires_at,
        created_by=user_id,
    )
    db.add(invitation)
    await db.commit()
    await db.refresh(invitation)
    return invitation


async def list_members(
    db: AsyncSession, org_id: int, user_id: int
) -> list[dict]:
    await _get_member(db, org_id, user_id)
    result = await db.execute(
        select(User, OrganizationMember.role, OrganizationMember.joined_at)
        .join(OrganizationMember, OrganizationMember.user_id == User.user_id)
        .where(OrganizationMember.org_id == org_id)
        .order_by(OrganizationMember.joined_at)
    )
    return [
        {
            "user_id": member.user_id,
            "username": member.username,
            "email": member.email,
            "full_name": member.full_name,
            "role": role,
            "joined_at": joined_at,
        }
        for member, role, joined_at in result.all()
    ]


async def update_member_role(
    db: AsyncSession,
    org_id: int,
    actor_id: int,
    target_id: int,
    payload: MemberRoleUpdate,
) -> OrganizationMember:
    actor = await _require_admin(db, org_id, actor_id)
    target = await db.scalar(
        select(OrganizationMember)
        .where(
            OrganizationMember.org_id == org_id,
            OrganizationMember.user_id == target_id,
        )
        .with_for_update()
    )
    if target is None:
        _fail(404, "Organization member not found")
    if target.role == "Owner":
        _fail(403, "The organization owner role cannot be changed here")
    if actor.role != "Owner" and (
        target.role == "Admin" or payload.role == "Admin"
    ):
        _fail(403, "Only the organization owner can manage Admin roles")
    target.role = payload.role
    await db.commit()
    await db.refresh(target)
    return target


async def remove_member(
    db: AsyncSession, org_id: int, actor_id: int, target_id: int
) -> None:
    actor = await _require_admin(db, org_id, actor_id)
    target = await db.scalar(
        select(OrganizationMember)
        .where(
            OrganizationMember.org_id == org_id,
            OrganizationMember.user_id == target_id,
        )
        .with_for_update()
    )
    if target is None:
        _fail(404, "Organization member not found")
    if target.role == "Owner":
        _fail(403, "The organization owner cannot be removed")
    if actor.role != "Owner" and target.role == "Admin":
        _fail(403, "Only the organization owner can remove Admin members")
    await db.delete(target)
    await db.commit()


async def list_groups(
    db: AsyncSession, org_id: int, user_id: int
) -> list[DeviceGroup]:
    await _get_member(db, org_id, user_id)
    result = await db.execute(
        select(DeviceGroup)
        .where(
            DeviceGroup.org_id == org_id,
            DeviceGroup.is_active.is_(True),
        )
        .order_by(DeviceGroup.group_name)
    )
    return list(result.scalars().all())


async def create_group(
    db: AsyncSession,
    org_id: int,
    user_id: int,
    payload: DeviceGroupCreate,
) -> DeviceGroup:
    await _require_admin(db, org_id, user_id)
    group = DeviceGroup(
        org_id=org_id,
        group_name=payload.group_name.strip(),
        description=payload.description,
        icon=payload.icon,
        color=payload.color,
    )
    db.add(group)
    await db.commit()
    await db.refresh(group)
    return group
