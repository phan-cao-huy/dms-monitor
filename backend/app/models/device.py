from datetime import datetime
from typing import Any

from sqlalchemy import BigInteger, DateTime, Float, ForeignKey, Integer, String, Text, func, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Device(Base):
    __tablename__ = "devices"

    device_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    org_id: Mapped[int] = mapped_column(
        ForeignKey("organizations.org_id", ondelete="CASCADE"), nullable=False
    )
    group_id: Mapped[int | None] = mapped_column(
        ForeignKey("device_groups.group_id", ondelete="SET NULL")
    )
    device_name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    secret_token_hash: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    secret_token_prefix: Mapped[str] = mapped_column(String(8), nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'Offline'")
    )
    last_heartbeat: Mapped[datetime | None] = mapped_column(DateTime)
    offline_threshold_minutes: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("5")
    )
    static_info: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    top_processes: Mapped[list[dict[str, Any]] | None] = mapped_column(JSONB)
    watch_config: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    watch_status: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    archived_at: Mapped[datetime | None] = mapped_column(DateTime)
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class DeviceMetric(Base):
    __tablename__ = "device_metrics"

    metric_id: Mapped[int] = mapped_column(
        BigInteger, primary_key=True, autoincrement=True
    )
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    cpu_usage: Mapped[float] = mapped_column(Float, nullable=False)
    ram_usage: Mapped[float] = mapped_column(Float, nullable=False)
    disk_usage: Mapped[float] = mapped_column(Float, nullable=False)
    net_in_kbps: Mapped[float] = mapped_column(
        Float, nullable=False, server_default=text("0")
    )
    net_out_kbps: Mapped[float] = mapped_column(
        Float, nullable=False, server_default=text("0")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime, primary_key=True, nullable=False, server_default=func.now()
    )


class DeviceMetricHourly(Base):
    __tablename__ = "device_metrics_hourly"

    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), primary_key=True
    )
    hour_bucket: Mapped[datetime] = mapped_column(DateTime, primary_key=True)
    cpu_avg: Mapped[float | None] = mapped_column(Float)
    cpu_max: Mapped[float | None] = mapped_column(Float)
    ram_avg: Mapped[float | None] = mapped_column(Float)
    ram_max: Mapped[float | None] = mapped_column(Float)
    disk_avg: Mapped[float | None] = mapped_column(Float)
    net_in_avg_kbps: Mapped[float | None] = mapped_column(Float)
    net_out_avg_kbps: Mapped[float | None] = mapped_column(Float)
    sample_count: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("0")
    )


class DeviceCommand(Base):
    __tablename__ = "device_commands"

    command_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    created_by: Mapped[int] = mapped_column(ForeignKey("users.user_id"), nullable=False)
    command_type: Mapped[str] = mapped_column(String(50), nullable=False)
    parameters: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'PENDING'")
    )
    output: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    completed_at: Mapped[datetime | None] = mapped_column(DateTime)
