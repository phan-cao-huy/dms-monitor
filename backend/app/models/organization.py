from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Organization(Base):
    __tablename__ = "organizations"

    org_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    logo_url: Mapped[str | None] = mapped_column(Text)
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.user_id"), nullable=False)
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("true")
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class OrganizationMember(Base):
    __tablename__ = "org_members"

    member_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_id: Mapped[int] = mapped_column(
        ForeignKey("organizations.org_id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False
    )
    role: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'Viewer'")
    )
    joined_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    invited_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    invite_code_used: Mapped[str | None] = mapped_column(String(6))


class OrganizationInvitation(Base):
    __tablename__ = "org_invitations"

    invite_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_id: Mapped[int] = mapped_column(
        ForeignKey("organizations.org_id", ondelete="CASCADE"), nullable=False
    )
    invite_code: Mapped[str] = mapped_column(String(6), unique=True, nullable=False)
    invite_token: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    default_role: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'Viewer'")
    )
    max_uses: Mapped[int | None] = mapped_column(Integer)
    use_count: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )
    expires_at: Mapped[datetime | None] = mapped_column(DateTime)
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("true")
    )
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class DeviceGroup(Base):
    __tablename__ = "device_groups"

    group_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_id: Mapped[int] = mapped_column(
        ForeignKey("organizations.org_id", ondelete="CASCADE"), nullable=False
    )
    group_name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    icon: Mapped[str | None] = mapped_column(
        String(50), server_default=text("'server'")
    )
    color: Mapped[str | None] = mapped_column(
        String(7), server_default=text("'#4A90E2'")
    )
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("true")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
