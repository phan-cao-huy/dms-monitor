"""Router cho module Device — 7 API theo API_CONTRACT.md muc Devices & Metrics."""

from datetime import datetime
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.schemas.device import (
    CommandCreate,
    CommandOut,
    DeviceCreate,
    DeviceCreateOut,
    DeviceDetailOut,
    DeviceListItem,
    DeviceUpdate,
    MetricHourlyOut,
    MetricOut,
    ProcessSnapshotOut,
)
from app.services.device_service import (
    create_command,
    create_device,
    delete_device,
    get_device_detail,
    get_latest_processes,
    get_metrics,
    list_commands,
    list_devices,
    update_device,
)

router = APIRouter(tags=["devices"])

DB = Annotated[AsyncSession, Depends(get_db)]
CurrentUser = Annotated[User, Depends(get_current_user)]


# ── 1. GET /orgs/{org_id}/devices ────────────────────────────────────────────


@router.get("/orgs/{org_id}/devices", response_model=list[DeviceListItem])
async def api_list_devices(
    org_id: int,
    db: DB,
    user: CurrentUser,
    group_id: Annotated[int | None, Query(description="Loc theo nhom")] = None,
    status_filter: Annotated[
        str | None, Query(alias="status", description="Loc theo trang thai")
    ] = None,
):
    """Lay danh sach thiet bi trong to chuc — cho Dashboard Mobile/Web."""
    try:
        devices = await list_devices(db, org_id, user.user_id, group_id, status_filter)
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    return devices


# ── 2. POST /orgs/{org_id}/devices ───────────────────────────────────────────


@router.post(
    "/orgs/{org_id}/devices",
    response_model=DeviceCreateOut,
    status_code=status.HTTP_201_CREATED,
)
async def api_create_device(
    org_id: int,
    body: DeviceCreate,
    db: DB,
    user: CurrentUser,
):
    """Tao thiet bi moi — tra ve secret_token CHI 1 LAN duy nhat."""
    try:
        device, raw_token = await create_device(
            db, org_id, user.user_id,
            body.device_name, body.description, body.group_id,
        )
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e)) from e

    return DeviceCreateOut(
        device_id=device.device_id,
        device_name=device.device_name,
        group_id=device.group_id,
        status=device.status,
        secret_token=raw_token,
        secret_token_prefix=device.secret_token_prefix,
        created_at=device.created_at,
    )


# ── 3. GET /devices/{id} ────────────────────────────────────────────────────


@router.get("/devices/{device_id}", response_model=DeviceDetailOut)
async def api_get_device(device_id: int, db: DB, user: CurrentUser):
    """Lay chi tiet thiet bi + thong tin phan cung tinh."""
    try:
        detail = await get_device_detail(db, device_id, user.user_id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    return detail


# ── 3b. PATCH /devices/{id} ─────────────────────────────────────────────────


@router.patch("/devices/{device_id}", response_model=DeviceDetailOut)
async def api_update_device(
    device_id: int, body: DeviceUpdate, db: DB, user: CurrentUser,
):
    """Cap nhat ten, mo ta, nhom cua thiet bi."""
    try:
        await update_device(
            db, device_id, user.user_id,
            body.device_name, body.description, body.group_id,
        )
        detail = await get_device_detail(db, device_id, user.user_id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except (PermissionError, ValueError) as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e)) from e
    return detail


# ── 3c. DELETE /devices/{id} ────────────────────────────────────────────────


@router.delete("/devices/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
async def api_delete_device(device_id: int, db: DB, user: CurrentUser):
    """Soft delete thiet bi (chuyen trang thai Archived)."""
    try:
        await delete_device(db, device_id, user.user_id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e


# ── 4. GET /devices/{id}/metrics ─────────────────────────────────────────────


@router.get("/devices/{device_id}/metrics")
async def api_get_metrics(
    device_id: int,
    db: DB,
    user: CurrentUser,
    from_dt: Annotated[datetime | None, Query(alias="from")] = None,
    to_dt: Annotated[datetime | None, Query(alias="to")] = None,
    interval: Annotated[Literal["raw", "hourly"], Query()] = "raw",
):
    """Lay du lieu metric theo khoang thoi gian — cho bieu do Mobile/Web.

    - interval=raw: tra tung diem 1 phut (toi da 1440 diem = 1 ngay)
    - interval=hourly: tra trung binh theo gio (cho bieu do 7 ngay / 30 ngay)
    """
    try:
        rows = await get_metrics(db, device_id, user.user_id, from_dt, to_dt, interval)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e

    if interval == "hourly":
        return [MetricHourlyOut.model_validate(r) for r in rows]
    return [MetricOut.model_validate(r) for r in rows]


# ── 5. GET /devices/{id}/processes ───────────────────────────────────────────


@router.get("/devices/{device_id}/processes", response_model=ProcessSnapshotOut | None)
async def api_get_processes(device_id: int, db: DB, user: CurrentUser):
    """Lay snapshot danh sach process moi nhat cua may."""
    try:
        snapshot = await get_latest_processes(db, device_id, user.user_id)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    if snapshot is None:
        return None
    return snapshot


# ── 6. POST /devices/{id}/commands ───────────────────────────────────────────


@router.post(
    "/devices/{device_id}/commands",
    response_model=CommandOut,
    status_code=status.HTTP_201_CREATED,
)
async def api_create_command(
    device_id: int, body: CommandCreate, db: DB, user: CurrentUser,
):
    """Gui lenh dieu khien tu xa (REBOOT / RESTART_SERVICE / KILL_PROCESS).

    Lenh se duoc luu vao hang doi voi trang thai PENDING.
    Agent se lay lenh nay qua GET /agent/commands va thuc thi.
    """
    try:
        cmd = await create_command(
            db, device_id, user.user_id,
            body.command_type, body.parameters, body.reason,
        )
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    return cmd


# ── 7. GET /devices/{id}/commands ────────────────────────────────────────────


@router.get("/devices/{device_id}/commands", response_model=list[CommandOut])
async def api_list_commands(
    device_id: int,
    db: DB,
    user: CurrentUser,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
):
    """Lay lich su cac lenh dieu khien da gui va ket qua thuc thi."""
    try:
        commands = await list_commands(db, device_id, user.user_id, limit)
    except LookupError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e)) from e
    except PermissionError as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e)) from e
    return commands
