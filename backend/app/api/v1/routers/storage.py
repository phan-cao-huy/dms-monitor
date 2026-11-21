from datetime import datetime, UTC
import os
import shutil

from fastapi import APIRouter, HTTPException
from sqlalchemy import text

from app.api.v1.routers.synthetic_checks import CurrentUser, DB
from app.core.config import settings
from app.services.alerts import AlertError, _get_org_member

router = APIRouter(tags=["storage"])

# pg_total_relation_size includes indexes and TOAST. Partitioned parents have
# no storage of their own; classify each physical child exactly once.
SIZES = """
SELECT CASE
    WHEN c.oid IN (SELECT inhrelid FROM pg_inherits WHERE inhparent='device_metrics'::regclass)
      THEN 'raw'
    WHEN c.relname='device_metrics_minute' THEN 'minute'
    WHEN c.relname='device_metrics_hourly' THEN 'hour'
    WHEN c.relname='device_events' THEN 'events'
    WHEN c.relname='check_results' THEN 'checks'
    WHEN c.relname='alert_history' THEN 'alerts'
    ELSE 'other' END AS kind,
    sum(pg_total_relation_size(c.oid))::bigint AS bytes
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r'
GROUP BY 1 ORDER BY 2 DESC
"""


@router.get("/orgs/{org_id}/storage")
async def storage(org_id: int, db: DB, user: CurrentUser):
    try:
        member = await _get_org_member(db, org_id, user.user_id)
    except AlertError as exc:
        raise HTTPException(exc.status_code, exc.detail) from exc
    if member.role not in ("Owner", "Admin"):
        raise HTTPException(403, "Workspace administrator required")

    groups = (await db.execute(text(SIZES))).mappings().all()
    days = dict(raw=settings.METRICS_RETENTION_DAYS, minute=settings.MINUTE_RETENTION_DAYS,
                hour=settings.HOURLY_RETENTION_DAYS, events=settings.EVENTS_RETENTION_DAYS, checks=7)
    mount = "/run/dms-db-volume"
    free = shutil.disk_usage(mount).free if os.path.ismount(mount) else None
    cleanup = (await db.execute(text("SELECT status, updated_at FROM storage_cleanup WHERE id=1"))).mappings().first()
    return dict(database_bytes=await db.scalar(text("SELECT pg_database_size(current_database())")),
                free_bytes=free, measured_at=datetime.now(UTC), cleanup=cleanup,
                groups=[dict(**row, days=days.get(row["kind"])) for row in groups])
