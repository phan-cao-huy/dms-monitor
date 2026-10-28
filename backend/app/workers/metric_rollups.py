"""Idempotent minute/hour aggregation; sums/counts preserve weighted averages."""
import logging
from datetime import UTC, datetime, timedelta

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert

from app.core.config import settings
from app.db.session import SessionLocal
from app.models.device import DeviceMetric, DeviceMetricHourly
from app.models.metrics import DeviceMetricMinute
from app.services.metric_history import LEGACY, METRICS, stat_parts

logger = logging.getLogger(__name__)


def raw_aggregate_stats():
    parts = []
    for metric in METRICS:
        value = getattr(DeviceMetric,metric)
        parts.extend([metric,func.jsonb_build_object("sum",func.sum(value),"count",func.count(value),"min",func.min(value),"max",func.max(value))])
    return func.jsonb_build_object(*parts)


async def rollup_window(db, start, end, now):
    minute = func.date_trunc("minute",DeviceMetric.created_at).label("minute_bucket")
    rows = select(DeviceMetric.device_id,minute,raw_aggregate_stats(),func.count()).where(
        DeviceMetric.created_at>=start,DeviceMetric.created_at<end).group_by(DeviceMetric.device_id,minute)
    stmt = insert(DeviceMetricMinute).from_select(["device_id","minute_bucket","metric_stats","sample_count"],rows)
    await db.execute(stmt.on_conflict_do_update(index_elements=["device_id","minute_bucket"],
        set_={"metric_stats":stmt.excluded.metric_stats,"sample_count":stmt.excluded.sample_count},
        where=stmt.excluded.sample_count>=DeviceMetricMinute.sample_count))

    hour = func.date_trunc("hour",DeviceMetricMinute.minute_bucket).label("hour_bucket")
    source = select(DeviceMetricMinute.device_id,DeviceMetricMinute.minute_bucket.label("stamp"),DeviceMetricMinute.metric_stats.label("stats"),DeviceMetricMinute.sample_count).where(
        DeviceMetricMinute.minute_bucket>=start.replace(minute=0,second=0,microsecond=0),
        DeviceMetricMinute.minute_bucket<min(end,now.replace(minute=0,second=0,microsecond=0))).subquery()
    hour = func.date_trunc("hour",source.c.stamp).label("hour_bucket")
    parts = []
    values = {}
    for metric in METRICS:
        stat = stat_parts(source,metric)
        total,count = func.sum(stat["sum"]),func.sum(stat["count"])
        parts.extend([metric,func.jsonb_build_object("sum",total,"count",count,"min",func.min(stat["min"]),"max",func.max(stat["max"]))])
        if metric in LEGACY:
            avg,max_column = LEGACY[metric]
            values[avg] = total/func.nullif(count,0)
            if max_column:
                values[max_column] = func.max(stat["max"])
    columns = ["device_id","hour_bucket","metric_stats","sample_count",*values]
    rows = select(source.c.device_id,hour,func.jsonb_build_object(*parts),func.sum(source.c.sample_count),*values.values()).group_by(source.c.device_id,hour)
    stmt = insert(DeviceMetricHourly).from_select(columns,rows)
    await db.execute(stmt.on_conflict_do_update(index_elements=["device_id","hour_bucket"],
        set_={key:getattr(stmt.excluded,key) for key in columns if key not in ("device_id","hour_bucket")},
        where=stmt.excluded.sample_count>=DeviceMetricHourly.sample_count))
    await db.commit()


async def aggregate_recent_metrics():
    now = datetime.now(UTC).replace(tzinfo=None)
    end = now.replace(second=0,microsecond=0)
    async with SessionLocal() as db:
        await rollup_window(db,end-timedelta(minutes=15),end,now)


async def backfill_metrics():
    now = datetime.now(UTC).replace(tzinfo=None)
    end = now.replace(second=0,microsecond=0)
    async with SessionLocal() as db:
        earliest = await db.scalar(select(func.min(DeviceMetric.created_at)).where(DeviceMetric.created_at>=now-timedelta(days=settings.METRICS_RETENTION_DAYS)))
    if earliest is None:
        return
    start = earliest.replace(minute=0,second=0,microsecond=0)
    while start<end:
        async with SessionLocal() as db:
            await rollup_window(db,start,min(start+timedelta(hours=1),end),now)
        start += timedelta(hours=1)
    logger.info("Metric minute/hour backfill completed")
