from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, model_validator


class AlertRuleCreate(BaseModel):
    group_id: int | None = None
    device_id: int | None = None
    rule_name: str | None = Field(default=None, max_length=150)
    metric_type: str = Field(
        pattern=r"^(CPU|RAM|GPU|DISK|NET_IN|NET_OUT|OFFLINE|SERVICE|PROCESS_MISSING)$"
    )
    condition: str = Field(default=">", pattern=r"^(>|>=|<|<=)$")
    threshold: float | None = Field(default=None, allow_inf_nan=False)
    service_name: str | None = Field(default=None, max_length=100)
    process_name: str | None = Field(default=None, max_length=100)
    duration_minutes: int = Field(default=1, ge=1)
    severity: str = Field(default="WARNING", pattern=r"^(INFO|WARNING|CRITICAL)$")
    cooldown_minutes: int = Field(default=30, ge=1)
    is_active: bool = True

    @model_validator(mode="after")
    def validate_target(self):
        if (self.device_id is None) == (self.group_id is None):
            raise ValueError("Specify exactly one device_id or group_id")
        if self.metric_type == "SERVICE" and not self.service_name:
            raise ValueError("SERVICE requires service_name")
        if self.metric_type == "PROCESS_MISSING" and not self.process_name:
            raise ValueError("PROCESS_MISSING requires process_name")
        if self.metric_type not in ("SERVICE", "PROCESS_MISSING") and self.threshold is None:
            raise ValueError("Numeric rules require threshold")
        if self.threshold is not None and self.metric_type not in ("SERVICE", "PROCESS_MISSING"):
            if self.threshold < 0:
                raise ValueError("Threshold must be nonnegative")
            if self.metric_type in ("CPU", "RAM", "GPU", "DISK") and self.threshold > 100:
                raise ValueError("Percentage threshold must be between 0 and 100")
            if self.metric_type == "OFFLINE" and self.threshold <= 0:
                raise ValueError("Offline threshold must be positive")
        return self


class AlertRuleUpdate(BaseModel):
    rule_name: str | None = Field(default=None, max_length=150)
    threshold: float | None = Field(default=None, allow_inf_nan=False)
    duration_minutes: int | None = Field(default=None, ge=1)
    cooldown_minutes: int | None = Field(default=None, ge=1)
    severity: str | None = Field(default=None, pattern=r"^(INFO|WARNING|CRITICAL)$")
    is_active: bool | None = None


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
    message_en: str | None = None
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
