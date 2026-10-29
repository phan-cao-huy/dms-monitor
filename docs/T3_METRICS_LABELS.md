# T3 — Tiered metric history, labels and query API

Web 1.0.23. Android support added in 1.0.24 / build 22; Agent stays at 1.3.0.
See [Android rollout](ANDROID_T1_T2_T3.md).
The existing Agent payload already includes GPU/CPU temperature when available;
this task starts persisting those fields in history. No old GPU/temperature history
is inferred from a device's latest snapshot.

## Web

- Device list: label chips and a **Lọc theo nhãn** filter, alongside status/group filters.
- Device detail → **Cấu hình → Nhãn thiết bị**: add, edit or remove labels.
- Device performance history: 1h/6h/24h/3d/7d/30d/90d, automatic resolution, GPU and CPU
  temperature charts, source tier and actual point spacing shown explicitly.
- Sidebar → **Phân tích tài nguyên**: select a metric, avg/min/max, group by device,
  device group or label key, optional label filter, time range and requested step.
  API access rules apply equally to all aggregations.
- At most 12 series are drawn together for readability, with a table for all returned
  series. Narrow by label when comparing a large workspace. Server limit is 100 series.
- Missing buckets are blank, not zero. The web inserts empty display buckets to avoid
  connecting a line across a period with no data. Device chart means use the number
  of samples per metric when available. Chart extrema refer to plotted points;
  use the query page's min/max for original sample extrema.

## Labels

`device_labels(device_id,key,value)` has a composite key and a lookup index.
At most 20 labels/device. Key: `^[a-z][a-z0-9_]{0,31}$`; value <=64 Unicode characters,
without control characters. Device-row locking prevents concurrent writes exceeding
the limit. Changing an existing value is allowed even when all 20 slots are occupied.

Read access follows device permissions. Owner/Admin/authorized Manager may write;
Viewer cannot. Workspace device-list label filters are applied before returning data,
and each device's labels are included in both list and detail responses.
Labels/group membership are current metadata: changing a label or group changes how
that device's historical metrics are grouped. Historical label assignments are not stored.

## Metric tiers and aggregation

- Raw retention: existing `METRICS_RETENTION_DAYS` (default 7).
- Minute retention: `MINUTE_RETENTION_DAYS` (default 30, minimum 3).
- Hourly retention: existing `HOURLY_RETENTION_DAYS` (default 90).
- Automatic source: <=6h raw, <=3d minute, >3d hourly.
- The worker upserts completed minutes every minute, rechecking the last 15 minutes.
  Completed hours are calculated from minute statistics. The current incomplete hour
  is not materialized as complete data.
- Startup and daily backfill process retained raw data in bounded one-hour transactions.
  Existing minute/hour rows are updated idempotently; a smaller partial raw set cannot
  overwrite a row with a larger sample count. The old hourly-only scheduler job is replaced.
- Each metric keeps sum/count/min/max. Group averages use `sum(all sums)/sum(all counts)`,
  not an average of per-device averages. GPU/temperature have their own non-null sample
  counts, so missing sensors do not change CPU/RAM weights or become zero readings.
- Minute queries also include raw samples for unrolled minutes. Hourly queries include
  minute/raw data for unrolled hours without counting already-rolled data twice.
- Historical source buckets whose start is before the requested start are omitted;
  a partial leading minute/hour cannot be reconstructed exactly without original samples.
- Existing legacy hourly averages and known CPU/RAM maxima remain usable. Historical
  minima, disk/network maxima and GPU/temperature that cannot be reconstructed remain
  null. Mixed buckets with unknown extrema do not claim a true min/max.

## API compatibility and limits

`GET /devices/{id}/metrics` accepts `interval=auto|raw|minute|hourly` (default auto).
The default response remains a list. Explicit `interval=raw`/`hourly` retain their
existing raw/hourly list shapes for Android and other old clients, with an added
`resolution` field. Explicit raw/hourly list limits retain the existing maximum 1000.

New web clients use `format=series&interval=auto&limit=500`:
```
{
  "resolution": "minute", "step_seconds": 180,
  "from": "...", "to": "...",
  "items": [{"device_id": 1, "created_at": "...", "cpu_usage": 40,
             "cpu_usage_samples": 18, "gpu_usage_pct": null, "gpu_usage_pct_samples": 0}]
}
```
Time bounds are UTC, half-open `[from,to)`, max 90 days. Auto/series device responses
and workspace queries have at most 500 points per series. Step is seconds, rounded up
to the selected source granularity and enlarged when necessary. Missing historical
values remain null; empty ranges return empty data plus resolution metadata.

`GET /orgs/{id}/metrics/query` whitelists seven metrics and avg/min/max. Grouping is
device/group/label:key, optionally filtered by key:value. Only authorized devices enter
the source subquery, before any aggregation. No user-supplied identifier is interpolated
into SQL. At most 100 returned series; excess returns 422 and asks for a narrower filter.

## Schema and deployment

`database/migrate_t3_metrics.sql` matches bundled `app/db/t3_metrics.sql`; both are included
in fresh-install `database/dms01.sql`. Backend startup applies the migration idempotently.
It adds nullable GPU/CPU-temperature columns to the partitioned raw parent (inherited by
partitions), metric_stats to the hourly table, minute history and device labels.

Back up PostgreSQL and `.env`, deploy backend first, then rebuild worker. Worker backfill
can take time on larger installations; API raw-tail fallback remains available meanwhile.
Changing retention settings requires recreating the corresponding containers.

## Validation

PostgreSQL tests cover migration reruns, existing raw partitions, weighted averages with
unequal device sample counts, per-metric GPU counts, legitimate zero GPU, null legacy
minima, scoped device/group/label queries, 20-label limit, Viewer rejection, label CRUD,
filtered lists, auto-resolution envelopes and explicit raw/hourly legacy responses.
Web tests cover label escaping/filtering, analytics rendering, weighted chart means and
missing-bucket gaps. Existing backend/web regressions remain included.
No browser visual QA or Android feature rollout is part of this web-first task.
