"""Prune complete device/time buckets only after validating their retained summary."""
from sqlalchemy import text

from app.services.metric_history import METRICS


def prune_statement(tier: str):
    if tier == "raw":
        source, stamp, unit = "device_metrics", "created_at", "minute"
        target, bucket = "device_metrics_minute", "minute_bucket"
        sample_count = "count(*)"
        dependency = "TRUE"
    elif tier == "minute":
        source, stamp, unit = "device_metrics_minute", "minute_bucket", "hour"
        target, bucket = "device_metrics_hourly", "hour_bucket"
        sample_count = "sum(s.sample_count)"
        # Any raw data still present may depend on these minute summaries.
        dependency = """NOT EXISTS (SELECT 1 FROM device_metrics r
            WHERE r.device_id=k.device_id AND r.created_at>=k.bucket
              AND r.created_at<k.bucket+interval '1 hour')"""
    else:
        raise ValueError("Unknown retention tier")

    checks = []
    for metric in METRICS:
        if tier == "minute":
            checks.append(f"bool_and(coalesce((s.metric_stats->'{metric}') ?& array['sum','count','min','max'], false))")
        for stat in ("sum", "count", "min", "max"):
            if tier == "raw":
                expected = f"{stat}(s.{metric})"
            else:
                op = "sum" if stat == "count" else stat
                expected = f"{op}((s.metric_stats->'{metric}'->>'{stat}')::float8)"
            actual = f"(t.metric_stats->'{metric}'->>'{stat}')::float8"
            comparison = f"({expected}) IS NOT DISTINCT FROM {actual}"
            if stat == "sum":
                # Summation order can differ between the worker and query plan.
                comparison = f"({comparison} OR abs(({expected})-{actual}) <= 1e-12 * greatest(1, abs({expected})))"
            checks.append(f"(t.metric_stats->'{metric}' ? '{stat}') AND ({comparison})")

    # One statement verifies and deletes from the same snapshot. Advance past
    # blocked buckets too, so a single incomplete summary cannot stall cleanup.
    return text(f"""
        WITH keys AS MATERIALIZED (
            SELECT DISTINCT device_id, date_trunc('{unit}', {stamp}) AS bucket
            FROM {source} WHERE {stamp}<:cutoff
              AND (device_id,date_trunc('{unit}',{stamp}))>(:last_id,:last_bucket)
            ORDER BY 1,2 LIMIT 500
        ), summaries AS MATERIALIZED (
            SELECT t.* FROM {target} t JOIN keys k
              ON t.device_id=k.device_id AND t.{bucket}=k.bucket FOR SHARE OF t
        ), verified AS (
            SELECT k.device_id,k.bucket FROM keys k JOIN summaries t
              ON t.device_id=k.device_id AND t.{bucket}=k.bucket
            JOIN {source} s ON s.device_id=k.device_id AND s.{stamp}>=k.bucket
                AND s.{stamp}<k.bucket+interval '1 {unit}'
            WHERE {dependency}
            GROUP BY k.device_id,k.bucket,t.sample_count,t.metric_stats
            HAVING {sample_count}=t.sample_count AND {" AND ".join(checks)}
        ), removed AS (
            DELETE FROM {source} s USING verified v WHERE s.device_id=v.device_id
              AND s.{stamp}>=v.bucket AND s.{stamp}<v.bucket+interval '1 {unit}'
        )
        SELECT device_id,bucket,(SELECT count(*) FROM keys)-(SELECT count(*) FROM verified) AS blocked
        FROM keys ORDER BY device_id DESC,bucket DESC LIMIT 1
    """)
