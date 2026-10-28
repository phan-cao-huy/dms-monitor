from datetime import UTC, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert

from app.core.config import settings
from app.models.monitoring import DeviceEvent
from app.services.devices import check_user_device_access


async def ingest_events(db, device, events):
    cutoff = datetime.now(UTC).replace(tzinfo=None) - timedelta(days=settings.EVENTS_RETENTION_DAYS)
    rows = []
    for event in events:
        row = event.model_dump()
        row["occurred_at"] = event.occurred_at.astimezone(UTC).replace(tzinfo=None)
        if row["occurred_at"] >= cutoff:
            rows.append({**row, "device_id": device.device_id})
    if rows:
        await db.execute(insert(DeviceEvent).values(rows).on_conflict_do_nothing())
    await db.commit()


async def list_events(db, device_id, user_id, level, before, limit):
    await check_user_device_access(db, device_id, user_id)
    stmt = select(DeviceEvent).where(DeviceEvent.device_id == device_id)
    if level:
        stmt = stmt.where(DeviceEvent.level == level)
    if before:
        stmt = stmt.where(DeviceEvent.event_pk < before)
    rows = list((await db.execute(stmt.order_by(DeviceEvent.event_pk.desc()).limit(limit+1))).scalars().all())
    return {"items": rows[:limit], "next_before": rows[limit-1].event_pk if len(rows) > limit else None}
