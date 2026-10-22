from typing import Any

from pydantic import BaseModel, Field, model_validator


class HeartbeatIn(BaseModel):
    """Payload Agent gui moi 60s.
    Ho tro ca 3 dinh dang:
    1. Flat (theo API contract): cpu_usage, ram_usage, disk_usage...
    2. Prompt spec: device_id, secret_token, cpu_usage, ram_usage...
    3. Nested (Go agent): device_id, secret_token, metrics: {...}, static_info: {...}
    """

    device_id: int | None = None
    secret_token: str | None = None

    cpu_usage: float = Field(default=0.0, ge=0, le=100)
    cpu_load_1m: float | None = None
    cpu_load_5m: float | None = None
    ram_usage: float = Field(default=0.0, ge=0, le=100)
    ram_used_mb: int | None = None
    disk_usage: float = Field(default=0.0, ge=0, le=100)
    disk_used_gb: float | None = None
    net_in_kbps: float = 0
    net_out_kbps: float = 0
    ping_latency_ms: float | None = None
    uptime_seconds: int | None = None
    agent_version: str | None = None

    # Nhiet do & GPU
    cpu_temp_c: float | None = None
    gpu_name: str | None = None
    gpu_usage_pct: float | None = None
    gpu_temp_c: float | None = None
    gpu_vram_total_mb: int | None = None
    gpu_vram_used_mb: int | None = None

    static_info: dict[str, Any] | None = None
    processes: list[dict[str, Any]] | None = None
    top_processes: list[dict[str, Any]] | None = None
    watch_status: dict[str, Any] | None = None

    # Danh sach tat ca o cung (moi o la dict: mountpoint, total_gb, used_gb, free_gb, used_percent)
    disks: list[dict[str, Any]] | None = None

    # Nested container from Go Agent
    metrics: dict[str, Any] | None = None

    @model_validator(mode="before")
    @classmethod
    def flatten_nested_metrics(cls, data: Any) -> Any:
        if isinstance(data, dict) and "metrics" in data and isinstance(data["metrics"], dict):
            m = data["metrics"]
            merged = {**data}
            for k in [
                "cpu_usage",
                "cpu_load_1m",
                "cpu_load_5m",
                "ram_usage",
                "ram_used_mb",
                "disk_usage",
                "disk_used_gb",
                "disks",
                "net_in_kbps",
                "net_out_kbps",
                "ping_latency_ms",
                "uptime_seconds",
                "agent_version",
                "processes",
                "cpu_temp_c",
                "gpu_name",
                "gpu_usage_pct",
                "gpu_temp_c",
                "gpu_vram_total_mb",
                "gpu_vram_used_mb",
            ]:
                if k in m and merged.get(k) is None:
                    merged[k] = m[k]
            if "processes" in m and not merged.get("top_processes"):
                merged["top_processes"] = m["processes"]
            return merged
        return data


class CommandOut(BaseModel):
    command_id: int
    command_type: str  # REBOOT | RESTART_SERVICE | KILL_PROCESS
    parameters: dict[str, Any] | None = None


class CommandListResponse(BaseModel):
    commands: list[CommandOut]


class CommandResultIn(BaseModel):
    command_id: int | None = None
    status: str  # SUCCESS | FAILED
    output: str | None = None
    error_message: str | None = None
