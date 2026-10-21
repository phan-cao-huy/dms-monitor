from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class AlertRuleCreate(BaseModel):
    group_id: int | None = None
    device_id: int | None = None
    rule_name: str | None = Field(default=None, max_length=150)
    metric_type: str = Field(
        pattern=r"^(CPU|RAM|DISK|NET_IN|NET_OUT|OFFLINE|SERVICE|PROCESS_MISSING)$"
    )
    condition: str = Field(default=">", pattern=r"^(>|>=|<|<=)$")
    threshold: float | None = None
    service_name: str | None = Field(default=None, max_length=100)
    process_name: str | None = Field(default=None, max_length=100)
    duration_minutes: int = Field(default=1, ge=1)
    severity: str = Field(default="WARNING", pattern=r"^(INFO|WARNING|CRITICAL)$")
    cooldown_minutes: int = Field(default=30, ge=1)


class AlertRuleOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    rule_id: int
    group_id: int | None
    device_id: int | None
    rule_name: str | None
    metric_type: str
    condition: str
    threshold: float | None
    service_name: str | None
    process_name: str | None
    duration_minutes: int
    severity: str
    cooldown_minutes: int
    is_active: bool
    created_at: datetime


class AlertHistoryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    alert_id: int
    device_id: int
    rule_id: int | None
    severity: str
    message: str
    metric_value: float | None
    threshold_value: float | None
    acknowledged_by: int | None
    acknowledged_at: datetime | None
    resolved_at: datetime | None
    notification_status: str
    notified_at: datetime | None
    created_at: datetime


class MaintenanceWindowCreate(BaseModel):
    device_id: int | None = None
    group_id: int | None = None
    title: str = Field(min_length=1, max_length=255)
    start_time: datetime
    end_time: datetime


class MaintenanceWindowOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    window_id: int
    device_id: int | None
    group_id: int | None
    title: str
    start_time: datetime
    end_time: datetime
    created_by: int | None
    created_at: datetime
