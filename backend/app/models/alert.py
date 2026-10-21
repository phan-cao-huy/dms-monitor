from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AlertRule(Base):
    __tablename__ = "alert_rules"

    rule_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_id: Mapped[int | None] = mapped_column(
        ForeignKey("device_groups.group_id", ondelete="CASCADE")
    )
    device_id: Mapped[int | None] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE")
    )
    rule_name: Mapped[str | None] = mapped_column(String(150))
    metric_type: Mapped[str] = mapped_column(String(20), nullable=False)
    condition: Mapped[str] = mapped_column(
        String(5), nullable=False, server_default=text("'>'")
    )
    threshold: Mapped[float | None] = mapped_column(Float)
    service_name: Mapped[str | None] = mapped_column(String(100))
    process_name: Mapped[str | None] = mapped_column(String(100))
    duration_minutes: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("1")
    )
    severity: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'WARNING'")
    )
    cooldown_minutes: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("30")
    )
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("true")
    )
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class AlertHistory(Base):
    __tablename__ = "alert_history"

    alert_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    rule_id: Mapped[int | None] = mapped_column(
        ForeignKey("alert_rules.rule_id", ondelete="SET NULL")
    )
    severity: Mapped[str] = mapped_column(String(20), nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    metric_value: Mapped[float | None] = mapped_column(Float)
    threshold_value: Mapped[float | None] = mapped_column(Float)
    acknowledged_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime)
    notification_status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'PENDING'")
    )
    notified_at: Mapped[datetime | None] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class MaintenanceWindow(Base):
    __tablename__ = "maintenance_windows"

    window_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int | None] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE")
    )
    group_id: Mapped[int | None] = mapped_column(
        ForeignKey("device_groups.group_id", ondelete="CASCADE")
    )
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    start_time: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    end_time: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
