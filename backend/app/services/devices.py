import hashlib
import logging
import secrets
from datetime import UTC, datetime, timedelta
from typing import Any, NoReturn

from sqlalchemy import and_, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

logger = logging.getLogger(__name__)

from app.models.alert import AlertHistory, AlertRule
from app.models.device import Device, DeviceCommand, DeviceMetric, DeviceMetricHourly
from app.models.organization import (
    DeviceGroup,
    Organization,
    OrganizationMember,
    UserGroupAccess,
)
from app.schemas.agent import CommandResultIn, HeartbeatIn
from app.schemas.device import DeviceCommandCreate, DeviceCreate, DeviceUpdate


class DeviceError(Exception):
    def __init__(self, status_code: int, detail: str) -> None:
        self.status_code = status_code
        self.detail = detail
        super().__init__(detail)


def _fail(status_code: int, detail: str) -> NoReturn:
    raise DeviceError(status_code, detail)


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


def _as_utc_naive(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is not None:
        return value.astimezone(UTC).replace(tzinfo=None)
    return value


def generate_device_token() -> tuple[str, str, str]:
    raw = "dms_" + secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    token_prefix = raw[:8]
    return raw, token_hash, token_prefix


def hash_token(token: str) -> str:
    return hashlib.sha256(token.strip().encode("utf-8")).hexdigest()


async def verify_device_credentials(
    db: AsyncSession, token: str, device_id: int | None = None
) -> Device:
    token_hash = hash_token(token)
    stmt = select(Device).where(
        Device.secret_token_hash == token_hash,
        or_(Device.archived_at.is_(None), select(DeviceCommand.command_id).where(
            DeviceCommand.device_id == Device.device_id,
            DeviceCommand.command_type == "UNINSTALL",
            or_(DeviceCommand.status.in_(("PENDING", "EXECUTING")),
                and_(DeviceCommand.status == "SUCCESS",
                     DeviceCommand.completed_at >= _utc_naive() - timedelta(minutes=5))),
        ).exists()),
    )
    if device_id is not None:
        stmt = stmt.where(Device.device_id == device_id)

    result = await db.execute(stmt)
    device = result.scalar_one_or_none()
    if device is None:
        _fail(401, "Invalid device credentials or device is archived")
    return device


async def _get_org_member(
    db: AsyncSession, org_id: int, user_id: int
) -> OrganizationMember:
    result = await db.execute(
        select(OrganizationMember)
        .join(Organization, Organization.org_id == OrganizationMember.org_id)
        .where(
            OrganizationMember.org_id == org_id,
            OrganizationMember.user_id == user_id,
            Organization.deleted_at.is_(None),
        )
    )
    member = result.scalar_one_or_none()
    if member is None:
        _fail(403, "You are not a member of this organization")
    return member


async def check_user_device_access(
    db: AsyncSession,
    device_id: int,
    user_id: int,
    required_roles: list[str] | None = None,
) -> tuple[Device, OrganizationMember]:
    result = await db.execute(
        select(Device).where(Device.device_id == device_id)
    )
    device = result.scalar_one_or_none()
    if device is None:
        _fail(404, "Device not found")

    member = await _get_org_member(db, device.org_id, user_id)

    if member.role in ("Owner", "Admin"):
        if required_roles and member.role not in required_roles:
            _fail(403, "Permission denied for this action")
        return device, member

    # For Manager / Viewer: check if group restriction applies
    if device.group_id is not None:
        acc_result = await db.execute(
            select(UserGroupAccess).where(
                UserGroupAccess.user_id == user_id,
                UserGroupAccess.group_id == device.group_id,
            )
        )
        access = acc_result.scalar_one_or_none()
        if access is None:
            _fail(403, "Bạn không có quyền truy cập cụm thiết bị này")
        role = access.role
    else:
        role = member.role

    if required_roles and role not in required_roles:
        _fail(403, f"Quyền {role} không được phép thực hiện hành động này (yêu cầu {', '.join(required_roles)})")

    return device, member


async def create_device(
    db: AsyncSession, org_id: int, user_id: int, payload: DeviceCreate
) -> tuple[Device, str, str | None]:
    member = await _get_org_member(db, org_id, user_id)
    if member.role not in ("Owner", "Admin"):
        _fail(403, "Only Owner or Admin can register devices")

    grp_name: str | None = None
    if payload.group_id is not None:
        group_res = await db.execute(
            select(DeviceGroup).where(
                DeviceGroup.group_id == payload.group_id,
                DeviceGroup.org_id == org_id,
            )
        )
        grp = group_res.scalar_one_or_none()
        if grp is None:
            _fail(404, "Device group not found in this organization")
        grp_name = grp.group_name

    raw_token, token_hash, token_prefix = generate_device_token()
    device = Device(
        org_id=org_id,
        group_id=payload.group_id,
        device_name=payload.device_name.strip(),
        description=payload.description,
        secret_token_hash=token_hash,
        secret_token_prefix=token_prefix,
        status="Offline",
        offline_threshold_minutes=payload.offline_threshold_minutes,
        created_by=user_id,
    )
    db.add(device)
    await db.commit()
    await db.refresh(device)
    return device, raw_token, grp_name


def _calculate_device_status(device: Device, now: datetime) -> str:
    if device.archived_at is not None or device.status == "Archived":
        return "Archived"
    if getattr(device, "is_monitoring", None) is False or device.status == "Paused":
        return "Paused"
    if device.last_heartbeat is None:
        return "Offline"
    diff_minutes = (now - device.last_heartbeat).total_seconds() / 60
    if diff_minutes > device.offline_threshold_minutes:
        return "Offline"
    return device.status if device.status in ("Online", "Warning") else "Online"


async def list_devices(
    db: AsyncSession, user_id: int, org_id: int | None = None
) -> list[dict[str, Any]]:
    now = _utc_naive()

    # Get user memberships
    stmt = (
        select(OrganizationMember)
        .join(Organization, Organization.org_id == OrganizationMember.org_id)
        .where(
            OrganizationMember.user_id == user_id,
            Organization.deleted_at.is_(None),
        )
    )
    if org_id is not None:
        stmt = stmt.where(OrganizationMember.org_id == org_id)

    members_res = await db.execute(stmt)
    members = list(members_res.scalars().all())
    if not members:
        if org_id is not None:
            _fail(403, "You are not a member of this organization")
        return []

    org_roles = {m.org_id: m.role for m in members}
    target_org_ids = list(org_roles.keys())

    # Get allowed groups for Manager/Viewer
    group_access_res = await db.execute(
        select(UserGroupAccess.group_id).where(UserGroupAccess.user_id == user_id)
    )
    accessible_group_ids = set(group_access_res.scalars().all())

    # Map group_id to group_name
    groups_res = await db.execute(
        select(DeviceGroup).where(DeviceGroup.org_id.in_(target_org_ids))
    )
    group_map = {g.group_id: g.group_name for g in groups_res.scalars().all()}

    dev_stmt = select(Device).where(
        Device.org_id.in_(target_org_ids),
        Device.archived_at.is_(None),
    ).order_by(Device.device_id.desc())

    dev_res = await db.execute(dev_stmt)
    all_devices = list(dev_res.scalars().all())

    result: list[dict[str, Any]] = []
    for d in all_devices:
        role = org_roles.get(d.org_id)
        if role in ("Owner", "Admin"):
            pass
        else:
            # Manager/Viewer: if group assigned, must have access
            if d.group_id is not None and d.group_id not in accessible_group_ids:
                continue

        computed_status = _calculate_device_status(d, now)

        result.append(
            {
                "device_id": d.device_id,
                "org_id": d.org_id,
                "group_id": d.group_id,
                "group_name": group_map.get(d.group_id) if d.group_id else None,
                "device_name": d.device_name,
                "description": d.description,
                "secret_token_prefix": d.secret_token_prefix,
                "status": computed_status,
                "last_heartbeat": d.last_heartbeat,
                "offline_threshold_minutes": d.offline_threshold_minutes,
                "is_monitoring": getattr(d, "is_monitoring", None) is not False,
                "created_at": d.created_at,
                "updated_at": d.updated_at,
            }
        )

    return result


async def get_device_detail(
    db: AsyncSession, device_id: int, user_id: int
) -> dict[str, Any]:
    now = _utc_naive()
    device, _ = await check_user_device_access(db, device_id, user_id)
    computed_status = _calculate_device_status(device, now)

    # Lay metric moi nhat de tra ve day du cac thong so % CPU, RAM, Disk, Net
    latest_res = await db.execute(
        select(DeviceMetric)
        .where(DeviceMetric.device_id == device_id)
        .order_by(DeviceMetric.created_at.desc())
        .limit(1)
    )
    latest_metric = latest_res.scalar_one_or_none()

    static = device.static_info or {}
    group_name = None
    if device.group_id:
        group_obj = await db.get(DeviceGroup, device.group_id)
        if group_obj:
            group_name = group_obj.group_name

    return {
        "device_id": device.device_id,
        "org_id": device.org_id,
        "group_id": device.group_id,
        "group_name": group_name,
        "device_name": device.device_name,
        "description": device.description,
        "secret_token_prefix": device.secret_token_prefix,
        "status": computed_status,
        "last_heartbeat": device.last_heartbeat,
        "offline_threshold_minutes": device.offline_threshold_minutes,
        "is_monitoring": getattr(device, "is_monitoring", None) is not False,
        "static_info": device.static_info,
        "top_processes": device.top_processes,
        "watch_config": device.watch_config,
        "watch_status": device.watch_status,
        "archived_at": device.archived_at,
        "created_at": device.created_at,
        "updated_at": device.updated_at,
        "cpu_usage": latest_metric.cpu_usage if latest_metric else None,
        "ram_usage": latest_metric.ram_usage if latest_metric else None,
        "disk_usage": latest_metric.disk_usage if latest_metric else None,
        "net_in_kbps": latest_metric.net_in_kbps if latest_metric else None,
        "net_out_kbps": latest_metric.net_out_kbps if latest_metric else None,
        "uptime_seconds": static.get("uptime_seconds"),
        "cpu_temp_c": static.get("cpu_temp_c"),
        "gpu_name": static.get("gpu_name"),
        "gpu_usage_pct": static.get("gpu_usage_pct"),
        "gpu_temp_c": static.get("gpu_temp_c"),
        "gpu_vram_total_mb": static.get("gpu_vram_total_mb"),
        "gpu_vram_used_mb": static.get("gpu_vram_used_mb"),
        # Danh sach tat ca o cung (lay tu static_info, duoc luu boi agent moi heartbeat)
        "disks": static.get("disks"),
    }


async def update_device(
    db: AsyncSession, device_id: int, user_id: int, payload: DeviceUpdate
) -> Device:
    device, _member = await check_user_device_access(
        db, device_id, user_id, required_roles=["Owner", "Admin", "Manager"]
    )

    if payload.group_id is not None:
        if payload.group_id <= 0:
            device.group_id = None
        else:
            group_res = await db.execute(
                select(DeviceGroup).where(
                    DeviceGroup.group_id == payload.group_id,
                    DeviceGroup.org_id == device.org_id,
                )
            )
            if group_res.scalar_one_or_none() is None:
                _fail(404, "Device group not found in this organization")
            device.group_id = payload.group_id

    if payload.device_name is not None:
        device.device_name = payload.device_name.strip()
    if payload.description is not None:
        device.description = payload.description
    if payload.offline_threshold_minutes is not None:
        device.offline_threshold_minutes = payload.offline_threshold_minutes
    if payload.watch_config is not None:
        device.watch_config = payload.watch_config

    await db.commit()
    await db.refresh(device)
    return device


async def regenerate_device_token(
    db: AsyncSession, device_id: int, user_id: int
) -> tuple[Device, str]:
    """Tao lai secret token moi cho thiet bi (token cu se mat hieu luc)."""
    device, _ = await check_user_device_access(
        db, device_id, user_id, required_roles=["Owner", "Admin"]
    )
    raw_token, token_hash, token_prefix = generate_device_token()
    device.secret_token_hash = token_hash
    device.secret_token_prefix = token_prefix
    await db.commit()
    await db.refresh(device)
    return device, raw_token


def _set_monitoring_state(device: Device, enabled: bool) -> None:
    device.is_monitoring = enabled
    device.updated_at = _utc_naive()
    heartbeat = _as_utc_naive(device.last_heartbeat)
    cutoff = _utc_naive() - timedelta(minutes=device.offline_threshold_minutes or 5)
    device.status = "Paused" if not enabled else (
        "Online" if heartbeat is not None and heartbeat >= cutoff else "Offline"
    )


async def toggle_device_monitoring(
    db: AsyncSession,
    device_id: int,
    user_id: int,
    is_monitoring: bool | None = None,
) -> dict[str, Any]:
    device, _ = await check_user_device_access(
        db, device_id, user_id, required_roles=["Owner", "Admin", "Manager"]
    )
    if device.archived_at is not None:
        _fail(409, "Cannot change monitoring for an archived device")
    current_state = getattr(device, "is_monitoring", None) is not False
    new_state = is_monitoring if is_monitoring is not None else not current_state
    _set_monitoring_state(device, new_state)

    cmd_type = "RESUME_MONITORING" if new_state else "PAUSE_MONITORING"
    reason = (
        "Người dùng kích hoạt lại theo dõi trên app"
        if new_state
        else "Người dùng tạm dừng theo dõi trên app"
    )

    toggle_cmd = DeviceCommand(
        device_id=device_id,
        created_by=user_id,
        command_type=cmd_type,
        parameters={},
        reason=reason,
        status="PENDING",
    )
    db.add(toggle_cmd)

    await db.commit()

    return {
        "device_id": device.device_id,
        "is_monitoring": device.is_monitoring,
        "status": device.status,
    }


async def archive_device(
    db: AsyncSession, device_id: int, user_id: int
) -> None:
    device, _ = await check_user_device_access(
        db, device_id, user_id, required_roles=["Owner", "Admin", "Manager"]
    )
    # Day lenh UNINSTALL xuong cho PC tu dong go bo va xoa toan bo file
    uninstall_cmd = DeviceCommand(
        device_id=device_id,
        created_by=user_id,
        command_type="UNINSTALL",
        parameters={"cleanup_dir": True, "stop_service": True},
        reason="Nguoi dung yeu cau xoa thiet bi va go sach phan mem khoi may tinh",
        status="PENDING",
    )
    db.add(uninstall_cmd)

    device.archived_at = _utc_naive()
    device.status = "Archived"

    await db.commit()


async def record_heartbeat(
    db: AsyncSession, device: Device, payload: HeartbeatIn
) -> None:
    now = _utc_naive()
    device.last_heartbeat = now

    if getattr(device, "is_monitoring", None) is False:
        device.status = "Paused"
        await db.commit()
        return

    current_static = dict(device.static_info or {})
    if payload.static_info is not None:
        current_static.update(payload.static_info)

    # Cap nhat thong so dynamic (Nhiet do, GPU) vao static_info de Android luon doc duoc
    for f_name in ["cpu_temp_c", "gpu_name", "gpu_usage_pct", "gpu_temp_c", "gpu_vram_total_mb", "gpu_vram_used_mb"]:
        val = getattr(payload, f_name, None)
        if val is not None:
            current_static[f_name] = val

    # Luu uptime vao static_info
    if payload.uptime_seconds is not None:
        current_static["uptime_seconds"] = payload.uptime_seconds

    # Luu danh sach tat ca o cung (disks) vao static_info
    if payload.disks is not None and len(payload.disks) > 0:
        current_static["disks"] = payload.disks

    device.static_info = current_static

    processes = payload.top_processes or payload.processes
    if processes is not None:
        device.top_processes = processes[:15]

    device.watch_status = payload.watch_status

    metric = DeviceMetric(
        device_id=device.device_id,
        cpu_usage=payload.cpu_usage,
        ram_usage=payload.ram_usage,
        disk_usage=payload.disk_usage,
        net_in_kbps=payload.net_in_kbps,
        net_out_kbps=payload.net_out_kbps,
        created_at=now,
    )
    db.add(metric)

    # Xu ly canh bao RAM >= 90% thoi gian thuc
    ram_usage = payload.ram_usage
    RAM_ALERT_THRESHOLD = 90.0
    RAM_ALERT_MESSAGE_PREFIX = "RAM vượt ngưỡng 90%"

    if hasattr(db, "execute"):
        custom_ram = (await db.execute(select(AlertRule.rule_id).where(
            AlertRule.is_active.is_(True), AlertRule.metric_type == "RAM",
            or_(AlertRule.device_id == device.device_id,
                AlertRule.group_id == device.group_id if device.group_id is not None else False),
        ).limit(1))).scalar_one_or_none()
        if custom_ram is not None:
            # Explicit rules replace the built-in RAM threshold for this device.
            ram_usage = -1

    if ram_usage >= RAM_ALERT_THRESHOLD:
        device.status = "Warning"
        message = (
            f"{RAM_ALERT_MESSAGE_PREFIX} "
            f"(thiết bị '{device.device_name}', hiện tại {ram_usage:.1f}%)."
        )
        active_alert = None
        if hasattr(db, "execute"):
            alert_stmt = (
                select(AlertHistory)
                .where(
                    AlertHistory.device_id == device.device_id,
                    AlertHistory.resolved_at.is_(None),
                    AlertHistory.message.startswith(RAM_ALERT_MESSAGE_PREFIX),
                )
                .order_by(AlertHistory.created_at.desc())
                .limit(1)
            )
            alert_res = await db.execute(alert_stmt)
            active_alert = alert_res.scalar_one_or_none()

        if active_alert is None:
            db.add(
                AlertHistory(
                    device_id=device.device_id,
                    severity="WARNING",
                    message=message,
                    metric_value=ram_usage,
                    threshold_value=RAM_ALERT_THRESHOLD,
                    notification_status="PENDING",
                )
            )
        else:
            active_alert.message = message
            active_alert.metric_value = ram_usage
            active_alert.threshold_value = RAM_ALERT_THRESHOLD
    else:
        # Neu RAM da ha xuong duoi 90%, tu dong giai quyet canh bao RAM cu (neu co)
        if hasattr(db, "execute"):
            alert_stmt = (
                select(AlertHistory)
                .where(
                    AlertHistory.device_id == device.device_id,
                    AlertHistory.resolved_at.is_(None),
                    AlertHistory.message.startswith(RAM_ALERT_MESSAGE_PREFIX),
                )
                .order_by(AlertHistory.created_at.desc())
                .limit(1)
            )
            alert_res = await db.execute(alert_stmt)
            active_alert = alert_res.scalar_one_or_none()
            if active_alert is not None:
                active_alert.resolved_at = now

            # Chi reset ve Online neu khong con canh bao nao khac dang mo
            other_stmt = (
                select(AlertHistory.alert_id)
                .where(
                    AlertHistory.device_id == device.device_id,
                    AlertHistory.resolved_at.is_(None),
                )
                .limit(1)
            )
            other_res = await db.execute(other_stmt)
            if other_res.scalar_one_or_none() is None:
                device.status = "Online"
        else:
            device.status = "Online"

    await db.commit()


async def get_device_metrics(
    db: AsyncSession,
    device_id: int,
    user_id: int,
    from_time: datetime | None = None,
    to_time: datetime | None = None,
    interval: str = "raw",
    limit: int = 100,
) -> list[Any]:
    await check_user_device_access(db, device_id, user_id)

    from_dt = _as_utc_naive(from_time)
    to_dt = _as_utc_naive(to_time)

    if interval == "hourly":
        stmt = select(DeviceMetricHourly).where(
            DeviceMetricHourly.device_id == device_id
        )
        if from_dt:
            stmt = stmt.where(DeviceMetricHourly.hour_bucket >= from_dt)
        if to_dt:
            stmt = stmt.where(DeviceMetricHourly.hour_bucket <= to_dt)
        stmt = stmt.order_by(DeviceMetricHourly.hour_bucket.asc()).limit(limit)
        res = await db.execute(stmt)
        return list(res.scalars().all())

    stmt = select(DeviceMetric).where(DeviceMetric.device_id == device_id)
    if from_dt:
        stmt = stmt.where(DeviceMetric.created_at >= from_dt)
    if to_dt:
        stmt = stmt.where(DeviceMetric.created_at <= to_dt)
    stmt = stmt.order_by(DeviceMetric.created_at.asc()).limit(limit)
    res = await db.execute(stmt)
    return list(res.scalars().all())


async def create_device_command(
    db: AsyncSession,
    device_id: int,
    user_id: int,
    payload: DeviceCommandCreate,
) -> DeviceCommand:
    device, _ = await check_user_device_access(
        db, device_id, user_id, required_roles=["Owner", "Admin", "Manager"]
    )

    if device.archived_at is not None:
        _fail(409, "Cannot send commands to an archived device")

    # Validate parameters per command_type
    cmd_type = payload.command_type.upper()
    params = payload.parameters or {}

    if cmd_type == "KILL_PROCESS":
        if "pid" not in params and "process_name" not in params:
            _fail(422, "KILL_PROCESS requires 'pid' or 'process_name' in parameters")
    elif cmd_type == "RESTART_SERVICE" and "service_name" not in params:
        _fail(422, "RESTART_SERVICE requires 'service_name' in parameters")

    if cmd_type in ("PAUSE_MONITORING", "RESUME_MONITORING"):
        _set_monitoring_state(device, cmd_type == "RESUME_MONITORING")
    elif cmd_type == "UNINSTALL":
        device.archived_at = _utc_naive()
        device.status = "Archived"

    cmd = DeviceCommand(
        device_id=device_id,
        created_by=user_id,
        command_type=cmd_type,
        parameters=params,
        reason=payload.reason.strip(),
        status="PENDING",
    )
    db.add(cmd)
    await db.commit()
    await db.refresh(cmd)
    return cmd


async def list_device_commands(
    db: AsyncSession, device_id: int, user_id: int, limit: int = 50
) -> list[DeviceCommand]:
    await check_user_device_access(db, device_id, user_id)
    stmt = (
        select(DeviceCommand)
        .where(DeviceCommand.device_id == device_id)
        .order_by(DeviceCommand.created_at.desc())
        .limit(limit)
    )
    res = await db.execute(stmt)
    return list(res.scalars().all())


async def fetch_pending_commands(
    db: AsyncSession, device: Device
) -> list[DeviceCommand]:
    stmt = (
        select(DeviceCommand)
        .where(
            DeviceCommand.device_id == device.device_id,
            or_(DeviceCommand.status == "PENDING",
                and_(DeviceCommand.command_type == "UNINSTALL", or_(
                    DeviceCommand.status == "EXECUTING",
                    and_(DeviceCommand.status == "SUCCESS",
                         DeviceCommand.completed_at >= _utc_naive() - timedelta(minutes=5))))),
            *([DeviceCommand.command_type == "UNINSTALL"] if device.archived_at else []),
        )
        .order_by(DeviceCommand.created_at.asc())
        .with_for_update()
    )
    res = await db.execute(stmt)
    commands = list(res.scalars().all())

    for cmd in commands:
        if cmd.status == "PENDING":
            cmd.status = "EXECUTING"

    if commands:
        await db.commit()
    return commands


async def record_command_result(
    db: AsyncSession,
    device: Device,
    command_id: int,
    payload: CommandResultIn,
) -> DeviceCommand:
    stmt = select(DeviceCommand).where(
        DeviceCommand.command_id == command_id,
        DeviceCommand.device_id == device.device_id,
    )
    res = await db.execute(stmt)
    cmd = res.scalar_one_or_none()
    if cmd is None:
        _fail(404, "Command not found for this device")

    if device.archived_at is not None and cmd.command_type != "UNINSTALL":
        _fail(403, "Archived devices may only report uninstall results")
    if payload.status.upper() not in ("SUCCESS", "FAILED"):
        _fail(422, "Invalid command result status")
    previous_status = cmd.status
    cmd.status = payload.status.upper()
    cmd.output = payload.output or payload.error_message
    if previous_status != "SUCCESS" or cmd.completed_at is None:
        cmd.completed_at = _utc_naive()

    await db.commit()
    await db.refresh(cmd)
    return cmd


async def agent_watch_config(db: AsyncSession, device: Device) -> dict:
    config = device.watch_config or {}
    services = {item["name"] for item in config.get("services", [])
                if isinstance(item, dict) and isinstance(item.get("name"), str)}
    processes = {name for name in config.get("processes", []) if isinstance(name, str)}
    rules = (await db.execute(select(AlertRule).where(
        AlertRule.is_active.is_(True), AlertRule.metric_type.in_(("SERVICE", "PROCESS_MISSING")),
        or_(AlertRule.device_id == device.device_id,
            AlertRule.group_id == device.group_id if device.group_id is not None else False),
    ))).scalars().all()
    for rule in rules:
        if rule.metric_type == "SERVICE" and rule.service_name:
            services.add(rule.service_name)
        elif rule.process_name:
            processes.add(rule.process_name)
    return {"services": [{"name": name} for name in sorted(services)], "processes": sorted(processes)}
