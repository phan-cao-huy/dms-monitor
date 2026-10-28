"""Metric whitelist, tier selection and weighted statistics shared by both query APIs."""
import math
from datetime import UTC, datetime, timedelta

from sqlalchemy import Float, case, cast, func, literal, select, union_all
from sqlalchemy.dialects.postgresql import JSONB

from app.models.device import DeviceMetric, DeviceMetricHourly
from app.models.metrics import DeviceMetricMinute
from app.services.devices import DeviceError

METRICS = ("cpu_usage", "ram_usage", "disk_usage", "net_in_kbps", "net_out_kbps", "gpu_usage_pct", "cpu_temp_c")
LEGACY = {
    "cpu_usage": ("cpu_avg", "cpu_max"), "ram_usage": ("ram_avg", "ram_max"),
    "disk_usage": ("disk_avg", None), "net_in_kbps": ("net_in_avg_kbps", None),
    "net_out_kbps": ("net_out_avg_kbps", None),
}


def window(start=None, end=None):
    end = end or datetime.now(UTC)
    start = start or end - timedelta(hours=1)
    start = start.astimezone(UTC).replace(tzinfo=None) if start.tzinfo else start
    end = end.astimezone(UTC).replace(tzinfo=None) if end.tzinfo else end
    if end <= start or end - start > timedelta(days=90):
        raise DeviceError(422, "Time range must be positive and at most 90 days")
    return start, end


def resolution_for(start, end):
    span = (end-start).total_seconds()
    return "raw" if span <= 21600 else "minute" if span <= 259200 else "hourly"


def step_for(start, end, resolution, requested=None, max_points=500):
    minimum = {"raw":1, "minute":60, "hourly":3600}[resolution]
    step = max(requested or minimum, math.ceil((end-start).total_seconds()/max_points), minimum)
    return math.ceil(step/minimum)*minimum


def raw_stats(metrics=METRICS):
    parts = []
    for metric in metrics:
        value = getattr(DeviceMetric, metric)
        parts.extend([metric, func.jsonb_build_object("sum", value, "count", case((value.is_not(None),1),else_=0), "min", value, "max", value)])
    return cast(func.jsonb_build_object(*parts),JSONB)


def hourly_stats(metrics=METRICS):
    parts = []
    for metric in metrics:
        legacy = LEGACY.get(metric)
        avg = getattr(DeviceMetricHourly, legacy[0]) if legacy else literal(None, Float)
        maximum = getattr(DeviceMetricHourly, legacy[1]) if legacy and legacy[1] else literal(None, Float)
        fallback = func.jsonb_build_object("sum", avg*DeviceMetricHourly.sample_count,
                                          "count", case((avg.is_not(None),DeviceMetricHourly.sample_count),else_=0),
                                          "min", literal(None,Float), "max", maximum)
        # A historical hourly average cannot reconstruct a historical minimum.
        parts.extend([metric, func.coalesce(DeviceMetricHourly.metric_stats[metric], fallback)])
    return cast(func.jsonb_build_object(*parts),JSONB)


def source_for(device_ids, start, end, resolution, metrics=METRICS):
    raw = select(DeviceMetric.device_id.label("device_id"), DeviceMetric.created_at.label("stamp"), raw_stats(metrics).label("stats")).where(
        DeviceMetric.device_id.in_(device_ids), DeviceMetric.created_at >= start, DeviceMetric.created_at < end)
    if resolution == "raw":
        return raw.subquery()
    minute_covered = select(DeviceMetricMinute.device_id).where(
        DeviceMetricMinute.device_id == DeviceMetric.device_id,
        DeviceMetricMinute.minute_bucket == func.date_trunc("minute", DeviceMetric.created_at)).exists()
    raw = raw.where(~minute_covered)
    minute = select(DeviceMetricMinute.device_id.label("device_id"), DeviceMetricMinute.minute_bucket.label("stamp"), DeviceMetricMinute.metric_stats.label("stats")).where(
        DeviceMetricMinute.device_id.in_(device_ids), DeviceMetricMinute.minute_bucket >= start, DeviceMetricMinute.minute_bucket < end)
    if resolution == "minute":
        return union_all(minute, raw).subquery()
    hour_for_raw = select(DeviceMetricHourly.device_id).where(
        DeviceMetricHourly.device_id == DeviceMetric.device_id,
        DeviceMetricHourly.hour_bucket == func.date_trunc("hour", DeviceMetric.created_at)).exists()
    hour_for_minute = select(DeviceMetricHourly.device_id).where(
        DeviceMetricHourly.device_id == DeviceMetricMinute.device_id,
        DeviceMetricHourly.hour_bucket == func.date_trunc("hour", DeviceMetricMinute.minute_bucket)).exists()
    hourly = select(DeviceMetricHourly.device_id.label("device_id"), DeviceMetricHourly.hour_bucket.label("stamp"), hourly_stats(metrics).label("stats")).where(
        DeviceMetricHourly.device_id.in_(device_ids), DeviceMetricHourly.hour_bucket >= start, DeviceMetricHourly.hour_bucket < end)
    return union_all(hourly, minute.where(~hour_for_minute), raw.where(~hour_for_raw)).subquery()


def stat_parts(source, metric):
    stat = source.c.stats[metric]
    return {key: cast(stat[key].astext, Float) for key in ("sum", "count", "min", "max")}


def aggregate_value(source, metric, agg):
    stat = stat_parts(source, metric)
    if agg == "avg":
        return func.sum(stat["sum"])/func.nullif(func.sum(stat["count"]),0)
    # A partial set of known extrema is not the true min/max of the whole bucket.
    complete = func.count(stat[agg]) == func.count(case((stat["count"]>0,1)))
    expression = func.min(stat[agg]) if agg == "min" else func.max(stat[agg])
    return case((complete,expression),else_=None)


async def device_series(db, device_id, start, end, resolution, limit=500):
    step = step_for(start,end,resolution,max_points=min(limit,500))
    source = source_for([device_id],start,end,resolution)
    bucket = func.date_bin(timedelta(seconds=step),source.c.stamp,start).label("bucket")
    rows = (await db.execute(select(bucket, *(aggregate_value(source,m,"avg").label(m) for m in METRICS),
                                    *(func.sum(stat_parts(source,m)["count"]).label(m+"_samples") for m in METRICS))
                             .group_by(bucket).order_by(bucket).limit(500))).all()
    return {"resolution":resolution,"step_seconds":step,"from":start.replace(tzinfo=UTC),"to":end.replace(tzinfo=UTC),
            "items":[{"device_id":device_id,"created_at":r.bucket.replace(tzinfo=UTC),"resolution":resolution,
                      "sample_count":int(r.cpu_usage_samples or 0),**{m:getattr(r,m) for m in METRICS},**{m+"_samples":int(getattr(r,m+"_samples") or 0) for m in METRICS}} for r in rows]}
