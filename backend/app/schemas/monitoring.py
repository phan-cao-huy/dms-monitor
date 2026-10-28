import ipaddress
from datetime import UTC, datetime, timedelta
from typing import Literal
from urllib.parse import urlsplit

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


class DiskHealthIn(BaseModel):
    name: str = Field(min_length=1, max_length=255)
    healthy: bool | None = None
    temp_c: float | None = Field(default=None, ge=-50, le=150, allow_inf_nan=False)
    wear_pct: float | None = Field(default=None, ge=0, le=100, allow_inf_nan=False)
    source: str | None = Field(default=None, max_length=100)


class ConnectionIn(BaseModel):
    pid: int = Field(ge=0)
    process: str = Field(max_length=255)
    laddr: str = Field(max_length=255)
    raddr: str = Field(max_length=255)
    status: Literal["ESTABLISHED", "LISTEN"]


class DeviceEventIn(BaseModel):
    log_name: Literal["System", "Application", "Security"]
    record_id: int = Field(gt=0, le=9223372036854775807)
    event_id: int = Field(ge=0, le=65535)
    level: Literal["ERROR", "CRITICAL", "AUDIT_FAILURE"]
    source: str = Field(max_length=255)
    message: str = Field(max_length=1000)
    occurred_at: AwareDatetime

    @model_validator(mode="after")
    def valid_event(self):
        if self.log_name == "Security" and (self.event_id != 4625 or self.level != "AUDIT_FAILURE"):
            raise ValueError("Only failed sign-in event 4625 is collected from Security")
        if self.log_name != "Security" and self.level == "AUDIT_FAILURE":
            raise ValueError("Audit failure belongs to Security")
        if self.occurred_at > datetime.now(UTC) + timedelta(minutes=5):
            raise ValueError("Event timestamp is in the future")
        return self


class DeviceEventsIn(BaseModel):
    events: list[DeviceEventIn] = Field(max_length=50)


class DeviceEventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    event_pk: int
    log_name: str
    record_id: int
    event_id: int
    level: str
    source: str
    message: str
    occurred_at: datetime


def target_parts(kind, target):
    if kind == "PING" and ":" in target and not target.startswith("["):
        ipaddress.ip_address(target)
        target = "[" + target + "]"
    url = urlsplit(target if kind in ("HTTP", "SSL") else "tcp://" + target)
    if not url.hostname or url.username is not None or url.password is not None or url.fragment:
        raise ValueError("Invalid target; credentials and fragments are not allowed")
    if kind in ("HTTP", "SSL"):
        if url.scheme not in ("http", "https") or (kind == "SSL" and url.scheme != "https"):
            raise ValueError("HTTP requires http(s) URL; SSL requires https URL")
    elif url.path or url.query or (kind == "PING" and url.port is not None):
        raise ValueError("PING accepts hostname/IP; TCP accepts host:port")
    port = url.port or (443 if url.scheme == "https" else 80)
    if kind == "TCP" and url.port is None:
        raise ValueError("TCP requires an explicit port")
    if not 1 <= port <= 65535:
        raise ValueError("Invalid port")
    return url.hostname, port, url


class CheckWrite(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=100)
    type: Literal["PING", "TCP", "HTTP", "SSL"]
    target: str = Field(min_length=1, max_length=2048)
    interval_seconds: int = Field(default=60, ge=30, le=86400)
    timeout_seconds: int = Field(default=5, ge=1, le=5)
    failure_threshold: int = Field(default=3, ge=1, le=20)
    enabled: bool = True

    @field_validator("target")
    @classmethod
    def clean_target(cls, value):
        if any(ord(c) <= 32 or ord(c) == 127 for c in value) or "\\" in value:
            raise ValueError("Target must not contain whitespace or control characters")
        return value

    @model_validator(mode="after")
    def valid_target(self):
        target_parts(self.type, self.target)
        return self
