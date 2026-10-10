from fastapi import APIRouter

from app.schemas.agent import CommandOut, CommandResultIn, HeartbeatIn

router = APIRouter(prefix="/agent", tags=["agent"])


@router.post("/heartbeat", status_code=202)
async def heartbeat(payload: HeartbeatIn):
    # TODO(Dev1): xac thuc device: sha256(secret_token) == devices.secret_token_hash
    # TODO(Dev1): INSERT device_metrics, UPDATE devices.last_heartbeat/status
    return {"accepted": True}


@router.get("/commands", response_model=list[CommandOut])
async def pending_commands():
    # TODO(Dev1): SELECT device_commands WHERE status='PENDING', doi sang EXECUTING
    return []


@router.post("/commands/{command_id}/result")
async def command_result(command_id: int, body: CommandResultIn):
    # TODO(Dev1): cap nhat status/output/completed_at
    return {"ok": True}
