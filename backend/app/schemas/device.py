"""Pydantic schemas cho module Device — validation dau vao/ra cua API."""

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


# ── Device CRUD ──────────────────────────────────────────────────────────────

CommandType = Literal["REBOOT", "RESTART_SERVICE", "KILL_PROCESS"]
DeviceStatus = Literal["Online", "Offline", "Warning", "Maintenance", "Archived"]


class DeviceCreate(BaseModel):
    """POST /orgs/{org_id}/devices — tao thiet bi moi."""

    device_name: str = Field(min_length=1, max_length=100)
    description: str | None = None
    group_id: int | None = None


class DeviceCreateOut(BaseModel):
    """Response khi tao thiet bi — co secret_token chi hien 1 lan duy nhat."""

    model_config = ConfigDict(from_attributes=True)

    device_id: int
    device_name: str
    group_id: int | None
    status: str
    secret_token: str  # chi tra 1 lan, KHONG luu plaintext
    secret_token_prefix: str
    created_at: datetime


class DeviceListItem(BaseModel):
    """GET /orgs/{org_id}/devices — item trong danh sach Dashboard."""

    model_config = ConfigDict(from_attributes=True)

    device_id: int
    device_name: str
    status: DeviceStatus
    group_id: int | None
    last_heartbeat: datetime | None
    # Metric moi nhat (lay tu bang device_metrics)
    cpu_usage: float | None = None
    ram_usage: float | None = None
    disk_usage: float | None = None


class DeviceUpdate(BaseModel):
    """PATCH /devices/{id} — cap nhat ten, nhom, mo ta."""

    device_name: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = None
    group_id: int | None = None


# ── Device Detail + Static Info ──────────────────────────────────────────────

class StaticInfoOut(BaseModel):
    """Thong tin phan cung tinh cua may."""

    model_config = ConfigDict(from_attributes=True)

    hostname: str | None = None
    os_name: str | None = None
    os_version: str | None = None
    kernel_version: str | None = None
    arch: str | None = None
    cpu_model: str | None = None
    cpu_cores: int | None = None
    ram_total_mb: int | None = None
    disk_total_gb: float | None = None
    public_ip: str | None = None
    local_ip: str | None = None
    mac_address: str | None = None
    agent_version: str | None = None
    last_updated: datetime | None = None


class DeviceDetailOut(BaseModel):
    """GET /devices/{id} — chi tiet may + static info."""

    model_config = ConfigDict(from_attributes=True)

    device_id: int
    device_name: str
    description: str | None
    status: str
    group_id: int | None
    last_heartbeat: datetime | None
    offline_threshold_minutes: int
    secret_token_prefix: str
    created_at: datetime
    updated_at: datetime
    created_by: int | None
    static_info: StaticInfoOut | None = None


# ── Metrics ──────────────────────────────────────────────────────────────────

class MetricOut(BaseModel):
    """1 diem du lieu metric — dung cho bieu do."""

    model_config = ConfigDict(from_attributes=True)

    cpu_usage: float
    ram_usage: float
    disk_usage: float
    net_in_kbps: float
    net_out_kbps: float
    ping_latency_ms: float | None = None
    uptime_seconds: int | None = None
    created_at: datetime


class MetricHourlyOut(BaseModel):
    """1 diem du lieu hourly aggregate."""

    model_config = ConfigDict(from_attributes=True)

    hour_bucket: datetime
    cpu_avg: float | None
    cpu_max: float | None
    ram_avg: float | None
    ram_max: float | None
    disk_avg: float | None
    net_in_avg_kbps: float | None
    net_out_avg_kbps: float | None
    ping_avg_ms: float | None
    sample_count: int


# ── Process Snapshots ────────────────────────────────────────────────────────

class ProcessSnapshotOut(BaseModel):
    """GET /devices/{id}/processes — snapshot process moi nhat."""

    model_config = ConfigDict(from_attributes=True)

    snapshot_id: int
    device_id: int
    processes: list[dict]
    created_at: datetime


# ── Commands ─────────────────────────────────────────────────────────────────

class CommandCreate(BaseModel):
    """POST /devices/{id}/commands — gui lenh dieu khien tu Mobile."""

    command_type: CommandType
    parameters: dict | None = None
    reason: str = Field(min_length=1, max_length=500)


class CommandOut(BaseModel):
    """Response cho lich su lenh."""

    model_config = ConfigDict(from_attributes=True)

    command_id: int
    device_id: int
    command_type: str
    parameters: dict | None
    reason: str
    status: str
    output: str | None
    error_message: str | None
    created_by: int
    created_at: datetime
    executed_at: datetime | None
    completed_at: datetime | None
