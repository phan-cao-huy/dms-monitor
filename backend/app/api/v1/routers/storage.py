from datetime import datetime, timedelta, UTC
from typing import Literal
import os
import shutil

from fastapi import APIRouter, HTTPException, Query
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


async def require_storage_admin(org_id: int, db: DB, user: CurrentUser):
    try:
        member = await _get_org_member(db, org_id, user.user_id)
    except AlertError as exc:
        raise HTTPException(exc.status_code, exc.detail) from exc
    if member.role not in ("Owner", "Admin"):
        raise HTTPException(403, "Workspace administrator required")

@router.get("/orgs/{org_id}/storage")
async def storage(org_id: int, db: DB, user: CurrentUser):
    await require_storage_admin(org_id, db, user)
    groups = (await db.execute(text(SIZES))).mappings().all()
    days = dict(raw=settings.METRICS_RETENTION_DAYS, minute=settings.MINUTE_RETENTION_DAYS,
                hour=settings.HOURLY_RETENTION_DAYS, events=settings.EVENTS_RETENTION_DAYS, checks=7)
    mount = "/run/dms-db-volume"
    free = shutil.disk_usage(mount).free if os.path.ismount(mount) else None
    cleanup = (await db.execute(text("SELECT status, updated_at FROM storage_cleanup WHERE id=1"))).mappings().first()
    return dict(database_bytes=await db.scalar(text("SELECT pg_database_size(current_database())")),
                free_bytes=free, measured_at=datetime.now(UTC), cleanup=cleanup,
                groups=[dict(**row, days=days.get(row["kind"])) for row in groups])

# Only server-defined identifiers are interpolated into this query.
POLICIES = {
    "raw": ("device_metrics", "created_at", "Mất chi tiết từng lần đo. Báo cáo cũ chỉ còn các bản tổng hợp đã lưu.", "Individual readings expire. Older reports rely on saved summaries."),
    "minute": ("device_metrics_minute", "minute_bucket", "Mất thống kê từng phút. Xu hướng dài hạn còn xem được ở những giờ đã tổng hợp.", "Minute statistics expire. Saved hourly summaries still show longer trends."),
    "hour": ("device_metrics_hourly", "hour_bucket", "Khi các mẫu gốc và bản theo phút cũng hết hạn, khoảng này sẽ mất khỏi báo cáo hiệu năng.", "Once raw and minute data also expire, this period disappears from performance reports."),
    "events": ("device_events", "occurred_at", "Các sự kiện Windows cũ sẽ mất khỏi danh sách tra cứu.", "Older Windows events disappear from event history."),
    "checks": ("check_results", "checked_at", "Mất kết quả từng lần kiểm tra dịch vụ. Lịch sử sự cố có thời hạn riêng.", "Individual service check results expire. Incidents have a separate retention period."),
}


@router.get("/orgs/{org_id}/storage/preview")
async def retention_preview(org_id: int, kind: Literal["raw", "minute", "hour", "events", "checks"],
                            db: DB, user: CurrentUser, days: int = Query(ge=1, le=3650)):
    await require_storage_admin(org_id, db, user)
    table, stamp, vi, en = POLICIES[kind]
    source = ("synthetic_checks USING (check_id)" if kind == "checks" else "devices USING (device_id)")
    cutoff = datetime.now(UTC).replace(tzinfo=None) - timedelta(days=days)
    row = (await db.execute(text(f"""
        SELECT count(*) AS records, min(t.{stamp}) AS oldest, max(t.{stamp}) AS newest
        FROM {table} t JOIN {source}
        WHERE org_id=:org AND t.{stamp}<:cutoff
    """), {"org": org_id, "cutoff": cutoff})).mappings().one()
    return dict(records=row["records"], oldest=row["oldest"], newest=row["newest"],
                days=days, impact_vi=vi, impact_en=en)
