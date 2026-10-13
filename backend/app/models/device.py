"""SQLAlchemy models cho module Device.

Mapping 1:1 voi cac bang trong database/schema.sql:
- devices, device_static_info, device_metrics,
- device_metrics_hourly, device_process_snapshots, device_commands
"""

from datetime import datetime

from sqlalchemy import (
    BigInteger,
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
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Device(Base):
    __tablename__ = "devices"

    device_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_id: Mapped[int | None] = mapped_column(
        ForeignKey("device_groups.group_id", ondelete="SET NULL")
    )
    device_name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    secret_token_hash: Mapped[str] = mapped_column(
        String(64), unique=True, nullable=False
    )
    secret_token_prefix: Mapped[str] = mapped_column(String(8), nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'Offline'")
    )
    last_heartbeat: Mapped[datetime | None] = mapped_column(DateTime)
    offline_threshold_minutes: Mapped[int] = mapped_column(
        Integer, nullable=False, server_default=text("5")
    )
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime)
    deleted_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.user_id"))


class DeviceStaticInfo(Base):
    __tablename__ = "device_static_info"

    info_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), unique=True, nullable=False
    )
    hostname: Mapped[str | None] = mapped_column(String(255))
    os_name: Mapped[str | None] = mapped_column(String(100))
    os_version: Mapped[str | None] = mapped_column(String(100))
    kernel_version: Mapped[str | None] = mapped_column(String(150))
    arch: Mapped[str | None] = mapped_column(String(20))
    cpu_model: Mapped[str | None] = mapped_column(String(255))
    cpu_cores: Mapped[int | None] = mapped_column(Integer)
    ram_total_mb: Mapped[int | None] = mapped_column(BigInteger)
    disk_total_gb: Mapped[float | None] = mapped_column(Float)
    public_ip: Mapped[str | None] = mapped_column(String(45))
    local_ip: Mapped[str | None] = mapped_column(String(45))
    mac_address: Mapped[str | None] = mapped_column(String(17))
    agent_version: Mapped[str | None] = mapped_column(String(20))
    last_updated: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class DeviceMetric(Base):
    __tablename__ = "device_metrics"

    metric_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    cpu_usage: Mapped[float] = mapped_column(Float, nullable=False)
    cpu_load_1m: Mapped[float | None] = mapped_column(Float)
    cpu_load_5m: Mapped[float | None] = mapped_column(Float)
    ram_usage: Mapped[float] = mapped_column(Float, nullable=False)
    ram_used_mb: Mapped[int | None] = mapped_column(BigInteger)
    disk_usage: Mapped[float] = mapped_column(Float, nullable=False)
    disk_used_gb: Mapped[float | None] = mapped_column(Float)
    net_in_kbps: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    net_out_kbps: Mapped[float] = mapped_column(Float, nullable=False, server_default=text("0"))
    ping_latency_ms: Mapped[float | None] = mapped_column(Float)
    uptime_seconds: Mapped[int | None] = mapped_column(BigInteger)
    agent_version: Mapped[str | None] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class DeviceMetricHourly(Base):
    __tablename__ = "device_metrics_hourly"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    hour_bucket: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    cpu_avg: Mapped[float | None] = mapped_column(Float)
    cpu_max: Mapped[float | None] = mapped_column(Float)
    ram_avg: Mapped[float | None] = mapped_column(Float)
    ram_max: Mapped[float | None] = mapped_column(Float)
    disk_avg: Mapped[float | None] = mapped_column(Float)
    net_in_avg_kbps: Mapped[float | None] = mapped_column(Float)
    net_out_avg_kbps: Mapped[float | None] = mapped_column(Float)
    ping_avg_ms: Mapped[float | None] = mapped_column(Float)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False)


class DeviceProcessSnapshot(Base):
    __tablename__ = "device_process_snapshots"

    snapshot_id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    processes: Mapped[dict] = mapped_column(JSONB, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )


class DeviceCommand(Base):
    __tablename__ = "device_commands"

    command_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        ForeignKey("devices.device_id", ondelete="CASCADE"), nullable=False
    )
    org_id: Mapped[int] = mapped_column(
        ForeignKey("organizations.org_id", ondelete="CASCADE"), nullable=False
    )
    created_by: Mapped[int] = mapped_column(
        ForeignKey("users.user_id"), nullable=False
    )
    command_type: Mapped[str] = mapped_column(String(50), nullable=False)
    parameters: Mapped[dict | None] = mapped_column(JSONB)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'PENDING'")
    )
    output: Mapped[str | None] = mapped_column(Text)
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )
    executed_at: Mapped[datetime | None] = mapped_column(DateTime)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime)
