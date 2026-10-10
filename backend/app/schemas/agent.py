from pydantic import BaseModel, Field


class HeartbeatIn(BaseModel):
    """Payload Agent gui moi 60s. Token o header Authorization: Bearer <secret_token>."""

    cpu_usage: float = Field(ge=0, le=100)
    cpu_load_1m: float | None = None
    cpu_load_5m: float | None = None
    ram_usage: float = Field(ge=0, le=100)
    ram_used_mb: int | None = None
    disk_usage: float = Field(ge=0, le=100)
    disk_used_gb: float | None = None
    net_in_kbps: float = 0
    net_out_kbps: float = 0
    ping_latency_ms: float | None = None
    uptime_seconds: int | None = None
    agent_version: str | None = None


class CommandOut(BaseModel):
    command_id: int
    command_type: str  # REBOOT | RESTART_SERVICE | KILL_PROCESS
    parameters: dict | None = None


class CommandResultIn(BaseModel):
    status: str  # SUCCESS | FAILED
    output: str | None = None
    error_message: str | None = None
