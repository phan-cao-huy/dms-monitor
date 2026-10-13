"""Service layer cho module Device — business logic tach khoi router."""

import secrets
from datetime import datetime, UTC

from sqlalchemy import select, update, desc
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_device_token
from app.models.device import (
    Device,
    DeviceCommand,
    DeviceMetric,
    DeviceMetricHourly,
    DeviceProcessSnapshot,
    DeviceStaticInfo,
)
from app.models.organization import DeviceGroup, OrganizationMember


# ── Helpers ──────────────────────────────────────────────────────────────────

async def _check_org_membership(
    db: AsyncSession, user_id: int, org_id: int
) -> OrganizationMember:
    """Kiem tra user co thuoc org khong, tra ve member row."""
    stmt = select(OrganizationMember).where(
        OrganizationMember.org_id == org_id,
        OrganizationMember.user_id == user_id,
    )
    member = (await db.execute(stmt)).scalar_one_or_none()
    if member is None:
        raise PermissionError("User is not a member of this organization")
    return member


async def _get_device_or_404(
    db: AsyncSession, device_id: int
) -> Device:
    """Lay device theo ID, raise neu khong tim thay hoac da xoa."""
    device = await db.get(Device, device_id)
    if device is None or device.deleted_at is not None:
        raise LookupError(f"Device {device_id} not found")
    return device


async def _get_org_id_for_device(db: AsyncSession, device_id: int) -> int:
    """Lay org_id tu device -> group -> org."""
    device = await _get_device_or_404(db, device_id)
    if device.group_id is None:
        raise LookupError("Device has no group")
    group = await db.get(DeviceGroup, device.group_id)
    if group is None:
        raise LookupError("Group not found")
    return group.org_id


# ── Device CRUD ──────────────────────────────────────────────────────────────

async def create_device(
    db: AsyncSession, org_id: int, user_id: int,
    device_name: str, description: str | None, group_id: int | None,
) -> tuple[Device, str]:
    """Tao thiet bi moi, sinh secret_token. Tra ve (device, raw_token)."""
    await _check_org_membership(db, user_id, org_id)

    # Validate group thuoc org
    if group_id is not None:
        group = await db.get(DeviceGroup, group_id)
        if group is None or group.org_id != org_id:
            raise ValueError("Group does not belong to this organization")

    # Sinh token: 32 byte -> 43 ky tu base64url
    raw_token = secrets.token_urlsafe(32)
    token_hash = hash_device_token(raw_token)
    token_prefix = raw_token[:8]

    device = Device(
        group_id=group_id,
        device_name=device_name,
        description=description,
        secret_token_hash=token_hash,
        secret_token_prefix=token_prefix,
        created_by=user_id,
    )
    db.add(device)
    await db.commit()
    await db.refresh(device)

    return device, raw_token


async def list_devices(
    db: AsyncSession, org_id: int, user_id: int,
    group_id: int | None = None, status_filter: str | None = None,
) -> list[dict]:
    """Lay danh sach thiet bi trong org (kem metric moi nhat)."""
    await _check_org_membership(db, user_id, org_id)

    # Lay tat ca group_id cua org
    group_stmt = select(DeviceGroup.group_id).where(DeviceGroup.org_id == org_id)
    group_ids = (await db.execute(group_stmt)).scalars().all()

    if not group_ids:
        return []

    stmt = select(Device).where(
        Device.group_id.in_(group_ids),
        Device.deleted_at.is_(None),
    )
    if group_id is not None:
        stmt = stmt.where(Device.group_id == group_id)
    if status_filter is not None:
        stmt = stmt.where(Device.status == status_filter)

    stmt = stmt.order_by(Device.device_name)
    devices = (await db.execute(stmt)).scalars().all()

    result = []
    for d in devices:
        # Lay metric moi nhat
        metric_stmt = (
            select(DeviceMetric)
            .where(DeviceMetric.device_id == d.device_id)
            .order_by(desc(DeviceMetric.created_at))
            .limit(1)
        )
        latest_metric = (await db.execute(metric_stmt)).scalar_one_or_none()

        result.append({
            "device_id": d.device_id,
            "device_name": d.device_name,
            "status": d.status,
            "group_id": d.group_id,
            "last_heartbeat": d.last_heartbeat,
            "cpu_usage": latest_metric.cpu_usage if latest_metric else None,
            "ram_usage": latest_metric.ram_usage if latest_metric else None,
            "disk_usage": latest_metric.disk_usage if latest_metric else None,
        })

    return result


async def get_device_detail(
    db: AsyncSession, device_id: int, user_id: int,
) -> dict:
    """Lay chi tiet thiet bi + static info."""
    device = await _get_device_or_404(db, device_id)
    org_id = await _get_org_id_for_device(db, device_id)
    await _check_org_membership(db, user_id, org_id)

    # Lay static info
    static_stmt = select(DeviceStaticInfo).where(
        DeviceStaticInfo.device_id == device_id
    )
    static_info = (await db.execute(static_stmt)).scalar_one_or_none()

    return {
        "device_id": device.device_id,
        "device_name": device.device_name,
        "description": device.description,
        "status": device.status,
        "group_id": device.group_id,
        "last_heartbeat": device.last_heartbeat,
        "offline_threshold_minutes": device.offline_threshold_minutes,
        "secret_token_prefix": device.secret_token_prefix,
        "created_at": device.created_at,
        "updated_at": device.updated_at,
        "created_by": device.created_by,
        "static_info": static_info,
    }


async def update_device(
    db: AsyncSession, device_id: int, user_id: int,
    device_name: str | None, description: str | None, group_id: int | None,
) -> Device:
    """Cap nhat ten, mo ta, nhom cua thiet bi."""
    device = await _get_device_or_404(db, device_id)
    org_id = await _get_org_id_for_device(db, device_id)
    await _check_org_membership(db, user_id, org_id)

    if device_name is not None:
        device.device_name = device_name
    if description is not None:
        device.description = description
    if group_id is not None:
        group = await db.get(DeviceGroup, group_id)
        if group is None or group.org_id != org_id:
            raise ValueError("Group does not belong to this organization")
        device.group_id = group_id

    device.updated_at = datetime.now(UTC)
    await db.commit()
    await db.refresh(device)
    return device


async def delete_device(
    db: AsyncSession, device_id: int, user_id: int,
) -> None:
    """Soft delete thiet bi."""
    device = await _get_device_or_404(db, device_id)
    org_id = await _get_org_id_for_device(db, device_id)
    member = await _check_org_membership(db, user_id, org_id)

    # Chi Owner/Admin duoc xoa
    if member.role not in ("Owner", "Admin"):
        raise PermissionError("Only Owner or Admin can delete devices")

    device.deleted_at = datetime.now(UTC)
    device.deleted_by = user_id
    device.status = "Archived"
    device.updated_at = datetime.now(UTC)
    await db.commit()


# ── Metrics ──────────────────────────────────────────────────────────────────

async def get_metrics(
    db: AsyncSession, device_id: int, user_id: int,
    from_dt: datetime | None, to_dt: datetime | None,
    interval: str = "raw",
) -> list:
    """Lay du lieu metric theo khoang thoi gian."""
    org_id = await _get_org_id_for_device(db, device_id)
    await _check_org_membership(db, user_id, org_id)

    if interval == "hourly":
        stmt = select(DeviceMetricHourly).where(
            DeviceMetricHourly.device_id == device_id
        )
        if from_dt:
            stmt = stmt.where(DeviceMetricHourly.hour_bucket >= from_dt)
        if to_dt:
            stmt = stmt.where(DeviceMetricHourly.hour_bucket <= to_dt)
        stmt = stmt.order_by(DeviceMetricHourly.hour_bucket)
        rows = (await db.execute(stmt)).scalars().all()
    else:
        stmt = select(DeviceMetric).where(DeviceMetric.device_id == device_id)
        if from_dt:
            stmt = stmt.where(DeviceMetric.created_at >= from_dt)
        if to_dt:
            stmt = stmt.where(DeviceMetric.created_at <= to_dt)
        stmt = stmt.order_by(DeviceMetric.created_at).limit(1440)  # Max 1 ngay raw
        rows = (await db.execute(stmt)).scalars().all()

    return rows


# ── Process Snapshots ────────────────────────────────────────────────────────

async def get_latest_processes(
    db: AsyncSession, device_id: int, user_id: int,
) -> DeviceProcessSnapshot | None:
    """Lay snapshot process moi nhat."""
    org_id = await _get_org_id_for_device(db, device_id)
    await _check_org_membership(db, user_id, org_id)

    stmt = (
        select(DeviceProcessSnapshot)
        .where(DeviceProcessSnapshot.device_id == device_id)
        .order_by(desc(DeviceProcessSnapshot.created_at))
        .limit(1)
    )
    return (await db.execute(stmt)).scalar_one_or_none()


# ── Commands ─────────────────────────────────────────────────────────────────

async def create_command(
    db: AsyncSession, device_id: int, user_id: int,
    command_type: str, parameters: dict | None, reason: str,
) -> DeviceCommand:
    """Tao lenh dieu khien — luu vao hang doi PENDING."""
    device = await _get_device_or_404(db, device_id)
    org_id = await _get_org_id_for_device(db, device_id)
    member = await _check_org_membership(db, user_id, org_id)

    # Chi Owner/Admin/Manager duoc gui lenh
    if member.role not in ("Owner", "Admin", "Manager"):
        raise PermissionError("Only Owner, Admin or Manager can send commands")

    cmd = DeviceCommand(
        device_id=device_id,
        org_id=org_id,
        created_by=user_id,
        command_type=command_type,
        parameters=parameters,
        reason=reason,
    )
    db.add(cmd)
    await db.commit()
    await db.refresh(cmd)
    return cmd


async def list_commands(
    db: AsyncSession, device_id: int, user_id: int,
    limit: int = 50,
) -> list[DeviceCommand]:
    """Lay lich su lenh dieu khien."""
    org_id = await _get_org_id_for_device(db, device_id)
    await _check_org_membership(db, user_id, org_id)

    stmt = (
        select(DeviceCommand)
        .where(DeviceCommand.device_id == device_id)
        .order_by(desc(DeviceCommand.created_at))
        .limit(limit)
    )
    return (await db.execute(stmt)).scalars().all()
