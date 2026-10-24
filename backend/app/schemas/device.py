from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class DeviceCreate(BaseModel):
    device_name: str = Field(min_length=1, max_length=100)
    description: str | None = None
    group_id: int | None = None
    offline_threshold_minutes: int = Field(default=5, ge=1, le=1440)


class DeviceCreateOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    device_id: int
    org_id: int
    group_id: int | None
    group_name: str | None = None
    device_name: str
    description: str | None
    secret_token: str
    secret_token_prefix: str
    status: str
    created_at: datetime
    # Lenh PowerShell 1 dong de cai Agent, chi hien 1 lan khi tao thiet bi
    install_command: str | None = None


class DeviceUpdate(BaseModel):
    device_name: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = None
    group_id: int | None = None
    offline_threshold_minutes: int | None = Field(default=None, ge=1, le=1440)
    watch_config: dict[str, Any] | None = None


class DeviceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    device_id: int
    org_id: int
    group_id: int | None
    group_name: str | None = None
    device_name: str
    description: str | None
    secret_token_prefix: str
    status: str
    is_monitoring: bool = True
    last_heartbeat: datetime | None
    offline_threshold_minutes: int
    cpu_usage: float | None = None
    ram_usage: float | None = None
    disk_usage: float | None = None
    created_at: datetime
    updated_at: datetime


class DeviceDetailOut(DeviceOut):
    static_info: dict[str, Any] | None = None
    top_processes: list[dict[str, Any]] | None = None
    watch_config: dict[str, Any] | None = None
    watch_status: dict[str, Any] | None = None
    archived_at: datetime | None = None

    # Dynamic metrics & thermal/GPU
    net_in_kbps: float | None = None
    net_out_kbps: float | None = None
    uptime_seconds: int | None = None
    cpu_temp_c: float | None = None
    gpu_name: str | None = None
    gpu_usage_pct: float | None = None
    gpu_temp_c: float | None = None
    gpu_vram_total_mb: int | None = None
    gpu_vram_used_mb: int | None = None

    # Danh sach tat ca o cung tu agent
    disks: list[dict[str, Any]] | None = None


class MetricOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    metric_id: int | None = None
    device_id: int
    cpu_usage: float
    ram_usage: float
    disk_usage: float
    net_in_kbps: float = 0
    net_out_kbps: float = 0
    created_at: datetime


class MetricHourlyOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    device_id: int
    hour_bucket: datetime
    cpu_avg: float | None = None
    cpu_max: float | None = None
    ram_avg: float | None = None
    ram_max: float | None = None
    disk_avg: float | None = None
    net_in_avg_kbps: float | None = None
    net_out_avg_kbps: float | None = None
    sample_count: int = 0


class DeviceCommandCreate(BaseModel):
    command_type: str = Field(pattern=r"^(REBOOT|RESTART_SERVICE|KILL_PROCESS|PAUSE_MONITORING|RESUME_MONITORING|UNINSTALL)$")
    parameters: dict[str, Any] | None = None
    reason: str = Field(min_length=1)


class DeviceCommandOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    command_id: int
    device_id: int
    created_by: int
    command_type: str
    parameters: dict[str, Any] | None = None
    reason: str
    status: str
    output: str | None = None
    created_at: datetime
    completed_at: datetime | None = None


class ToggleMonitoringIn(BaseModel):
    is_monitoring: bool | None = None
