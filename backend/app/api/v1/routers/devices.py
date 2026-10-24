from collections.abc import Awaitable
from datetime import datetime
from typing import Annotated, Any, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.schemas.device import (
    DeviceCommandCreate,
    DeviceCommandOut,
    DeviceCreate,
    DeviceCreateOut,
    DeviceDetailOut,
    DeviceOut,
    DeviceUpdate,
    MetricHourlyOut,
    MetricOut,
    ToggleMonitoringIn,
)
from app.services.devices import (
    DeviceError,
    archive_device,
    create_device,
    create_device_command,
    get_device_detail,
    get_device_metrics,
    list_device_commands,
    list_devices,
    regenerate_device_token,
    toggle_device_monitoring,
    update_device,
)

router = APIRouter(tags=["devices"])
Result = TypeVar("Result")


async def _run(operation: Awaitable[Result]) -> Result:
    try:
        return await operation
    except DeviceError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail=exc.detail,
        ) from exc


@router.get(
    "/devices",
    response_model=list[DeviceOut],
    summary="Lấy danh sách thiết bị",
)
async def get_all_devices(
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    org_id: int | None = Query(default=None, description="Lọc theo tổ chức"),
) -> list[dict[str, Any]]:
    """Lấy danh sách các thiết bị người dùng có quyền truy cập."""
    return await _run(list_devices(db, user.user_id, org_id))


@router.get(
    "/orgs/{org_id}/devices",
    response_model=list[DeviceOut],
    summary="Lấy danh sách thiết bị theo tổ chức",
)
async def get_org_devices(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[dict[str, Any]]:
    """Lấy danh sách thiết bị thuộc tổ chức cụ thể."""
    return await _run(list_devices(db, user.user_id, org_id))


@router.post(
    "/orgs/{org_id}/devices",
    response_model=DeviceCreateOut,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo thiết bị mới cho tổ chức",
)
async def post_device(
    org_id: int,
    payload: DeviceCreate,
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict[str, Any]:
    """Tạo thiết bị mới và trả về secret_token duy nhất 1 lần để cấu hình Agent."""
    device, raw_token, grp_name = await _run(
        create_device(db, org_id, user.user_id, payload)
    )
    # Tao lenh cai dat 1 dong cho nguoi dung copy truc tiep
    base_url = str(request.base_url).rstrip("/")
    install_cmd = (
        f"& ([scriptblock]::Create((irm '{base_url}/static/install.ps1'))) "
        f"-Server {base_url} -Token {raw_token} -DeviceId {device.device_id}"
    )
    return {
        "device_id": device.device_id,
        "org_id": device.org_id,
        "group_id": device.group_id,
        "group_name": grp_name,
        "device_name": device.device_name,
        "description": device.description,
        "secret_token": raw_token,
        "secret_token_prefix": device.secret_token_prefix,
        "status": device.status,
        "created_at": device.created_at,
        "install_command": install_cmd,
    }


@router.get(
    "/devices/{device_id}",
    response_model=DeviceDetailOut,
    summary="Chi tiết thiết bị",
)
async def get_device(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict[str, Any]:
    """Lấy thông tin chi tiết thiết bị bao gồm static_info, watch_status và top_processes."""
    return await _run(get_device_detail(db, device_id, user.user_id))


@router.patch(
    "/devices/{device_id}",
    response_model=DeviceOut,
    summary="Cập nhật thiết bị",
)
async def patch_device(
    device_id: int,
    payload: DeviceUpdate,
    ToggleMonitoringIn,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Any:
    """Cập nhật tên, mô tả, nhóm hoặc cấu hình theo dõi của thiết bị."""
    return await _run(update_device(db, device_id, user.user_id, payload))


@router.post(
    "/devices/{device_id}/toggle-monitoring",
    summary="Bật / Tắt theo dõi thiết bị",
)
async def post_toggle_monitoring(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    payload: ToggleMonitoringIn | None = None,
) -> dict[str, Any]:
    """Bật hoặc tạm dừng theo dõi thiết bị từ App Mobile."""
    is_mon = payload.is_monitoring if payload else None
    return await _run(toggle_device_monitoring(db, device_id, user.user_id, is_mon))


@router.delete(
    "/devices/{device_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Lưu trữ / xóa thiết bị",
)
async def delete_device_endpoint(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Đánh dấu thiết bị đã lưu trữ (Archived) và ngừng giám sát."""
    await _run(archive_device(db, device_id, user.user_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get(
    "/devices/{device_id}/metrics",
    response_model=list[MetricOut] | list[MetricHourlyOut],
    summary="Lấy lịch sử metric thiết bị",
)
async def get_metrics(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    from_time: Annotated[datetime | None, Query(alias="from")] = None,
    to_time: Annotated[datetime | None, Query(alias="to")] = None,
    interval: Annotated[str, Query(pattern=r"^(raw|hourly)$")] = "raw",
    limit: Annotated[int, Query(ge=1, le=1000)] = 100,
) -> Any:
    """Lấy dữ liệu hiệu năng CPU/RAM/Disk/Network theo chu kỳ raw (thô) hoặc hourly (tổng hợp theo giờ)."""
    return await _run(
        get_device_metrics(
            db, device_id, user.user_id, from_time, to_time, interval, limit
        )
    )


@router.get(
    "/devices/{device_id}/processes",
    summary="Lấy snapshot tiến trình mới nhất",
)
async def get_processes(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[dict[str, Any]]:
    """Trả về danh sách 15 tiến trình sử dụng tài nguyên cao nhất từ heartbeat gần nhất."""
    detail = await _run(get_device_detail(db, device_id, user.user_id))
    return detail.get("top_processes") or []


@router.post(
    "/devices/{device_id}/regenerate-token",
    summary="Tạo lại secret token (token cũ mất hiệu lực)",
)
async def post_regenerate_token(
    device_id: int,
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> dict[str, Any]:
    """Reset secret token — dùng khi token bị lộ hoặc muốn lấy lại install_command."""
    device, raw_token = await _run(regenerate_device_token(db, device_id, user.user_id))
    base_url = str(request.base_url).rstrip("/")
    install_cmd = (
        f"& ([scriptblock]::Create((irm '{base_url}/static/install.ps1'))) "
        f"-Server {base_url} -Token {raw_token} -DeviceId {device.device_id}"
    )
    return {
        "device_id": device.device_id,
        "secret_token": raw_token,
        "secret_token_prefix": device.secret_token_prefix,
        "install_command": install_cmd,
        "note": "Token cu da mat hieu luc. Agent dang chay can cap nhat config.json.",
    }


@router.post(
    "/devices/{device_id}/commands",
    response_model=DeviceCommandOut,
    status_code=status.HTTP_201_CREATED,
    summary="Gửi lệnh điều khiển tới thiết bị",
)
async def post_command(
    device_id: int,
    payload: DeviceCommandCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Any:
    """Gửi yêu cầu điều khiển từ xa (REBOOT, RESTART_SERVICE, KILL_PROCESS) kèm lý do bắt buộc."""
    return await _run(
        create_device_command(db, device_id, user.user_id, payload)
    )


@router.get(
    "/devices/{device_id}/commands",
    response_model=list[DeviceCommandOut],
    summary="Lấy lịch sử lệnh điều khiển",
)
async def get_commands(
    device_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    limit: int = Query(default=50, ge=1, le=200),
) -> Any:
    """Lấy lịch sử các lệnh đã gửi tới thiết bị này kèm trạng thái và kết quả trả về."""
    return await _run(list_device_commands(db, device_id, user.user_id, limit))
