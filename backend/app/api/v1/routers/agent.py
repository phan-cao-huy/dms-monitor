from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_agent_device, get_optional_agent_device
from app.db.session import get_db
from app.models.device import Device
from app.schemas.agent import (
    CommandListResponse,
    CommandOut,
    CommandResultIn,
    HeartbeatIn,
)
from app.services.devices import (
    DeviceError,
    fetch_pending_commands,
    record_command_result,
    record_heartbeat,
    verify_device_credentials,
)

router = APIRouter(prefix="/agent", tags=["agent"])


@router.post(
    "/heartbeat",
    status_code=status.HTTP_202_ACCEPTED,
    summary="Gửi heartbeat từ Agent",
)
async def heartbeat(
    payload: HeartbeatIn,
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
    device: Annotated[Device | None, Depends(get_optional_agent_device)],
) -> dict[str, bool]:
    """Nhận heartbeat định kỳ từ Agent, cập nhật trạng thái thiết bị và lưu metrics."""
    if device is None:
        if payload.secret_token:
            try:
                device = await verify_device_credentials(
                    db, payload.secret_token, payload.device_id
                )
            except DeviceError as exc:
                raise HTTPException(
                    status_code=exc.status_code, detail=exc.detail
                ) from exc
        else:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Missing or invalid device credentials",
                headers={"WWW-Authenticate": "Bearer"},
            )

    await record_heartbeat(db, device, payload)
    return {"accepted": True}


@router.get(
    "/commands",
    response_model=list[CommandOut] | CommandListResponse,
    summary="Lấy các lệnh điều khiển đang chờ",
)
async def pending_commands(
    request: Request,
    db: Annotated[AsyncSession, Depends(get_db)],
    device: Annotated[Device, Depends(get_current_agent_device)],
    format: str | None = None,
) -> Any:
    """Trả về các lệnh điều khiển PENDING cho thiết bị này và chuyển trạng thái sang EXECUTING."""
    commands = await fetch_pending_commands(db, device)
    cmd_outs = [
        CommandOut(
            command_id=cmd.command_id,
            command_type=cmd.command_type,
            parameters=cmd.parameters,
        )
        for cmd in commands
    ]

    ua = request.headers.get("user-agent", "")
    if ua.startswith("Go-http-client") or format == "object":
        return CommandListResponse(commands=cmd_outs)

    return cmd_outs


@router.post(
    "/commands/{command_id}/result",
    summary="Báo cáo kết quả lệnh điều khiển (POST)",
)
@router.put(
    "/commands/{command_id}/result",
    summary="Báo cáo kết quả lệnh điều khiển (PUT)",
)
async def command_result(
    command_id: int,
    body: CommandResultIn,
    db: Annotated[AsyncSession, Depends(get_db)],
    device: Annotated[Device, Depends(get_current_agent_device)],
) -> dict[str, bool]:
    """Cập nhật trạng thái và output/error của lệnh điều khiển."""
    try:
        await record_command_result(db, device, command_id, body)
    except DeviceError as exc:
        raise HTTPException(
            status_code=exc.status_code, detail=exc.detail
        ) from exc
    return {"ok": True}
