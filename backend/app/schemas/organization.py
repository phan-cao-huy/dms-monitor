from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


MemberRole = Literal["Owner", "Admin", "Manager", "Viewer"]
InviteRole = Literal["Manager", "Viewer"]


class OrganizationCreate(BaseModel):
    org_name: str = Field(min_length=1, max_length=100)
    description: str | None = None
    logo_url: str | None = None


class OrganizationOut(BaseModel):
    org_id: int
    org_name: str
    description: str | None
    logo_url: str | None
    owner_id: int
    role: MemberRole
    created_at: datetime


class JoinOrganizationIn(BaseModel):
    invite_code: str = Field(min_length=6, max_length=6, pattern=r"^[A-Za-z0-9]{6}$")


class JoinOrganizationOut(BaseModel):
    org_id: int
    user_id: int
    role: MemberRole
    joined_at: datetime


class InvitationCreate(BaseModel):
    default_role: InviteRole = "Viewer"
    max_uses: int | None = Field(default=None, ge=1)
    expires_at: datetime | None = None


class InvitationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    invite_id: int
    org_id: int
    invite_code: str
    invite_token: str
    default_role: InviteRole
    max_uses: int | None
    use_count: int
    expires_at: datetime | None
    is_active: bool
    created_by: int | None
    created_at: datetime


class MemberOut(BaseModel):
    user_id: int
    username: str
    email: str
    full_name: str | None
    role: MemberRole
    joined_at: datetime


class MemberRoleUpdate(BaseModel):
    role: Literal["Admin", "Manager", "Viewer"]


class DeviceGroupCreate(BaseModel):
    group_name: str = Field(min_length=1, max_length=100)
    description: str | None = None
    icon: str | None = Field(default="server", max_length=50)
    color: str | None = Field(default="#4A90E2", pattern=r"^#[0-9A-Fa-f]{6}$")


class DeviceGroupOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    group_id: int
    org_id: int
    group_name: str
    description: str | None
    icon: str | None
    color: str | None
    is_active: bool
    created_at: datetime
