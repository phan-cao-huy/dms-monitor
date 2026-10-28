from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class DeviceEvent(Base):
    __tablename__ = "device_events"
    __table_args__ = (UniqueConstraint("device_id", "log_name", "record_id", "occurred_at"), Index("ix_device_events_device_cursor", "device_id", "event_pk"))
    event_pk: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.device_id", ondelete="CASCADE"), index=True)
    log_name: Mapped[str] = mapped_column(String(20))
    record_id: Mapped[int] = mapped_column(BigInteger)
    event_id: Mapped[int] = mapped_column(Integer)
    level: Mapped[str] = mapped_column(String(20))
    source: Mapped[str] = mapped_column(String(255))
    message: Mapped[str] = mapped_column(Text)
    occurred_at: Mapped[datetime] = mapped_column(DateTime, index=True)


class SyntheticCheck(Base):
    __tablename__ = "synthetic_checks"
    check_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_id: Mapped[int] = mapped_column(ForeignKey("organizations.org_id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(100))
    type: Mapped[str] = mapped_column(String(10))
    target: Mapped[str] = mapped_column(String(2048))
    interval_seconds: Mapped[int] = mapped_column(Integer, default=60)
    timeout_seconds: Mapped[int] = mapped_column(Integer, default=5)
    failure_threshold: Mapped[int] = mapped_column(Integer, default=3)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    consecutive_failures: Mapped[int] = mapped_column(Integer, default=0)
    incident_open: Mapped[bool] = mapped_column(Boolean, default=False)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class CheckResult(Base):
    __tablename__ = "check_results"
    __table_args__ = (Index("ix_check_results_check_cursor", "check_id", "result_id"),)
    result_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    check_id: Mapped[int] = mapped_column(ForeignKey("synthetic_checks.check_id", ondelete="CASCADE"), index=True)
    ok: Mapped[bool] = mapped_column(Boolean)
    latency_ms: Mapped[float | None] = mapped_column(Float)
    detail: Mapped[str] = mapped_column(String(100))
    checked_at: Mapped[datetime] = mapped_column(DateTime, default=func.now(), index=True)


class CheckAudit(Base):
    __tablename__ = "check_audit"
    audit_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    org_id: Mapped[int] = mapped_column(ForeignKey("organizations.org_id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("users.user_id", ondelete="SET NULL"))
    check_id: Mapped[int | None] = mapped_column(Integer)
    action: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class CheckIncident(Base):
    __tablename__ = "check_incidents"
    __table_args__ = (Index("uq_check_open_incident", "check_id", unique=True, postgresql_where=text("resolved_at IS NULL")),)
    incident_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    check_id: Mapped[int] = mapped_column(ForeignKey("synthetic_checks.check_id", ondelete="CASCADE"), index=True)
    detail: Mapped[str] = mapped_column(String(100))
    opened_at: Mapped[datetime] = mapped_column(DateTime, default=func.now())
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime)


class CheckEmailDelivery(Base):
    __tablename__ = "check_email_deliveries"
    __table_args__ = (UniqueConstraint("incident_id", "channel_id"),)
    delivery_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    incident_id: Mapped[int] = mapped_column(ForeignKey("check_incidents.incident_id", ondelete="CASCADE"))
    channel_id: Mapped[int] = mapped_column(ForeignKey("email_channels.channel_id", ondelete="CASCADE"))
    status: Mapped[str] = mapped_column(String(20), default="PENDING")
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    last_error: Mapped[str | None] = mapped_column(String(100))
    last_attempt_at: Mapped[datetime | None] = mapped_column(DateTime)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime)
