from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_agent_device, get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.device import Device
from app.schemas.monitoring import DeviceEventOut, DeviceEventsIn
from app.services.device_events import ingest_events, list_events
from app.services.devices import DeviceError

router = APIRouter(tags=["device-monitoring"])


@router.post("/agent/events", status_code=202)
async def post_events(payload: DeviceEventsIn, db: Annotated[AsyncSession, Depends(get_db)],
                      device: Annotated[Device, Depends(get_current_agent_device)]):
    if device.archived_at is not None or device.is_monitoring is False:
        raise HTTPException(403, "Monitoring is disabled or device is archived")
    await ingest_events(db, device, payload.events)
    return {"accepted": True}


@router.get("/devices/{device_id}/events")
async def get_events(device_id: int, db: Annotated[AsyncSession, Depends(get_db)],
                     user: Annotated[User, Depends(get_current_user)],
                     level: Literal["ERROR", "CRITICAL", "AUDIT_FAILURE"] | None = None,
                     before: int | None = Query(default=None, gt=0), limit: int = Query(default=50, ge=1, le=100)):
    try:
        page = await list_events(db, device_id, user.user_id, level, before, limit)
    except DeviceError as exc:
        raise HTTPException(exc.status_code, exc.detail) from exc
    return {"items": [DeviceEventOut.model_validate(e) for e in page["items"]], "next_before": page["next_before"]}
