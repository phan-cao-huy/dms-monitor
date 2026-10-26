"""Background workers (APScheduler). Chạy riêng: python -m app.workers.runner

Các tác vụ định kỳ:
- offline_detector   : mỗi 1 phút, nếu quá offline_threshold_minutes -> status='Offline' + ghi alert
- command_timeout    : mỗi 1 phút, lệnh PENDING/EXECUTING quá 5 phút -> TIMEOUT
- partition_manager  : mỗi ngày/Chủ nhật, gọi create_metrics_partition()
- hourly_aggregator  : mỗi giờ, tổng hợp device_metrics -> device_metrics_hourly
"""

import asyncio
import logging
from contextlib import asynccontextmanager, suppress
from datetime import UTC, datetime, timedelta
from pathlib import Path

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import delete, func, select, text, tuple_

from app.core.config import settings
from app.db.session import SessionLocal, engine
from app.models.alert import AlertHistory, AlertRuleState
from app.models.device import Device, DeviceCommand, DeviceMetric, DeviceMetricHourly

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] (Worker) %(message)s",
)
logger = logging.getLogger("worker")
RAM_ALERT_THRESHOLD = 90.0
RAM_ALERT_MESSAGE_PREFIX = "RAM vượt ngưỡng 90%"


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


async def check_offline_devices() -> None:
    now = _utc_naive()
    async with SessionLocal() as db:
        try:
            stmt = select(Device).where(
                Device.archived_at.is_(None),
                Device.status.in_(("Online", "Warning")),
                Device.last_heartbeat.is_not(None),
            )
            res = await db.execute(stmt)
            devices = list(res.scalars().all())

            for d in devices:
                diff_min = (now - d.last_heartbeat).total_seconds() / 60
                if diff_min > d.offline_threshold_minutes:
                    d.status = "Offline"
                    alert = AlertHistory(
                        device_id=d.device_id,
                        severity="CRITICAL",
                        message=f"Thiết bị '{d.device_name}' (ID: {d.device_id}) mất kết nối ({int(diff_min)} phút).",
                        metric_value=diff_min,
                        threshold_value=float(d.offline_threshold_minutes),
                        notification_status="PENDING",
                    )
                    db.add(alert)
                    logger.warning(
                        f"Thiết bị #{d.device_id} '{d.device_name}' chuyển sang Offline."
                    )

            await db.commit()
        except Exception:
            logger.exception("Lỗi kiểm tra offline")


async def check_high_ram_devices() -> None:
    """Ghi cảnh báo khi RAM vượt 90% và tự đóng khi mức dùng hạ xuống."""
    now = _utc_naive()
    async with SessionLocal() as db:
        try:
            result = await db.execute(
                select(Device).where(
                    Device.archived_at.is_(None),
                    Device.status.in_(("Online", "Warning")),
                )
            )
            devices = list(result.scalars().all())

            for device in devices:
                metric_result = await db.execute(
                    select(DeviceMetric)
                    .where(DeviceMetric.device_id == device.device_id)
                    .order_by(DeviceMetric.created_at.desc(), DeviceMetric.metric_id.desc())
                    .limit(1)
                )
                metric = metric_result.scalar_one_or_none()
                if metric is None:
                    continue

                alert_result = await db.execute(
                    select(AlertHistory)
                    .where(
                        AlertHistory.device_id == device.device_id,
                        AlertHistory.resolved_at.is_(None),
                        AlertHistory.message.startswith(RAM_ALERT_MESSAGE_PREFIX),
                    )
                    .order_by(AlertHistory.created_at.desc())
                    .limit(1)
                )
                active_alert = alert_result.scalar_one_or_none()

                if metric.ram_usage >= RAM_ALERT_THRESHOLD:
                    device.status = "Warning"
                    message = (
                        f"{RAM_ALERT_MESSAGE_PREFIX} "
                        f"(thiết bị '{device.device_name}', hiện tại {metric.ram_usage:.1f}%)."
                    )
                    if active_alert is None:
                        db.add(
                            AlertHistory(
                                device_id=device.device_id,
                                severity="WARNING",
                                message=message,
                                metric_value=metric.ram_usage,
                                threshold_value=RAM_ALERT_THRESHOLD,
                                notification_status="PENDING",
                            )
                        )
                    else:
                        active_alert.message = message
                        active_alert.metric_value = metric.ram_usage
                        active_alert.threshold_value = RAM_ALERT_THRESHOLD
                else:
                    if active_alert is not None:
                        active_alert.resolved_at = now
                    if device.status == "Warning":
                        other_alert_res = await db.execute(
                            select(AlertHistory.alert_id).where(
                                AlertHistory.device_id == device.device_id,
                                AlertHistory.resolved_at.is_(None),
                                AlertHistory.alert_id != (active_alert.alert_id if active_alert else 0),
                            ).limit(1)
                        )
                        if other_alert_res.scalar_one_or_none() is None:
                            device.status = "Online"

            await db.commit()
        except Exception:
            logger.exception("Lỗi kiểm tra cảnh báo RAM")


async def check_command_timeouts() -> None:
    now = _utc_naive()
    timeout_threshold = now - timedelta(minutes=5)
    async with SessionLocal() as db:
        try:
            stmt = select(DeviceCommand).where(
                DeviceCommand.status.in_(("PENDING", "EXECUTING")),
                DeviceCommand.created_at < timeout_threshold,
                DeviceCommand.command_type != "UNINSTALL",
            )
            res = await db.execute(stmt)
            timed_out = list(res.scalars().all())

            for cmd in timed_out:
                cmd.status = "TIMEOUT"
                cmd.completed_at = now
                cmd.output = "Command timed out after 5 minutes without response."
                logger.info(f"Lệnh #{cmd.command_id} bị timeout.")

            if timed_out:
                await db.commit()
        except Exception:
            logger.exception("Lỗi kiểm tra timeout lệnh")


async def aggregate_hourly_metrics() -> None:
    now = _utc_naive()
    curr_hour = now.replace(minute=0, second=0, microsecond=0)
    prev_hour = curr_hour - timedelta(hours=1)

    async with SessionLocal() as db:
        try:
            stmt = (
                select(
                    DeviceMetric.device_id,
                    func.avg(DeviceMetric.cpu_usage).label("cpu_avg"),
                    func.max(DeviceMetric.cpu_usage).label("cpu_max"),
                    func.avg(DeviceMetric.ram_usage).label("ram_avg"),
                    func.max(DeviceMetric.ram_usage).label("ram_max"),
                    func.avg(DeviceMetric.disk_usage).label("disk_avg"),
                    func.avg(DeviceMetric.net_in_kbps).label("net_in_avg"),
                    func.avg(DeviceMetric.net_out_kbps).label("net_out_avg"),
                    func.count(DeviceMetric.metric_id).label("sample_count"),
                )
                .where(
                    DeviceMetric.created_at >= prev_hour,
                    DeviceMetric.created_at < curr_hour,
                )
                .group_by(DeviceMetric.device_id)
            )
            res = await db.execute(stmt)
            rows = res.fetchall()

            for row in rows:
                existing = await db.get(
                    DeviceMetricHourly, (row.device_id, prev_hour)
                )
                if existing is None:
                    hourly = DeviceMetricHourly(
                        device_id=row.device_id,
                        hour_bucket=prev_hour,
                        cpu_avg=row.cpu_avg,
                        cpu_max=row.cpu_max,
                        ram_avg=row.ram_avg,
                        ram_max=row.ram_max,
                        disk_avg=row.disk_avg,
                        net_in_avg_kbps=row.net_in_avg,
                        net_out_avg_kbps=row.net_out_avg,
                        sample_count=row.sample_count,
                    )
                    db.add(hourly)

            if rows:
                await db.commit()
                logger.info(f"Tổng hợp {len(rows)} bản ghi hourly cho bucket {prev_hour}.")
        except Exception:
            logger.exception("Lỗi tổng hợp hourly")


async def manage_partitions() -> None:
    # Each week is atomic, including rows already in the DEFAULT partition.
    for offset in (0, 7):
        async with engine.begin() as conn:
            await conn.execute(text("SET LOCAL lock_timeout = '5s'"))
            await conn.execute(text("SET LOCAL statement_timeout = '60s'"))
            await conn.execute(text(Path(__file__).resolve().parents[1].joinpath("db/partition_function.sql").read_text()))
            await conn.execute(text("SELECT create_metrics_partition(:d)"), {"d": _utc_naive().date() + timedelta(days=offset)})


async def cleanup_metrics() -> None:
    # Bound each transaction; composite keys are unique across partitions.
    targets = [
        (DeviceMetric, (DeviceMetric.metric_id, DeviceMetric.created_at), DeviceMetric.created_at, settings.METRICS_RETENTION_DAYS),
        (DeviceMetricHourly, (DeviceMetricHourly.device_id, DeviceMetricHourly.hour_bucket), DeviceMetricHourly.hour_bucket, settings.HOURLY_RETENTION_DAYS),
    ]
    for model, keys, timestamp, days in targets:
        cutoff = _utc_naive() - timedelta(days=days)
        for _ in range(100):
            async with SessionLocal() as db:
                batch = select(*keys).where(timestamp < cutoff).limit(10000)
                result = await db.execute(delete(model).where(tuple_(*keys).in_(batch)))
                await db.commit()
                if result.rowcount < 10000:
                    break


def build_scheduler() -> AsyncIOScheduler:
    from app.workers.notifications import send_pending_notifications
    from app.workers.rules import evaluate_alert_rules

    scheduler = AsyncIOScheduler(timezone="UTC", job_defaults={"max_instances": 1, "coalesce": True})
    scheduler.add_job(check_offline_devices, "interval", minutes=1)
    scheduler.add_job(check_command_timeouts, "interval", minutes=1)
    scheduler.add_job(evaluate_alert_rules, "interval", minutes=1)
    scheduler.add_job(send_pending_notifications, "interval", minutes=1)
    scheduler.add_job(aggregate_hourly_metrics, "cron", minute=5)
    scheduler.add_job(manage_partitions, "cron", hour=0, minute=0)
    scheduler.add_job(cleanup_metrics, "cron", hour=1, minute=0)
    return scheduler


@asynccontextmanager
async def worker_lifespan():
    # Session lock is shared by embedded mode and dedicated workers.
    async with engine.connect() as conn:
        acquired = (await conn.execute(text("SELECT pg_try_advisory_lock(70412026)"))).scalar()
        await conn.commit()
        if not acquired:
            logger.info("Another DMS worker owns the scheduler lock")
            yield None
            return
        scheduler = None
        try:
            async with engine.begin() as migration:
                await migration.run_sync(lambda c: AlertRuleState.__table__.create(c, checkfirst=True))
            try:
                await manage_partitions()
            except Exception:
                logger.exception("Partition initialization failed; daily job will retry")
            scheduler = build_scheduler()
            scheduler.start()
            async def monitor_connection():
                try:
                    while True:
                        await asyncio.sleep(10)
                        await conn.execute(text("SELECT 1"))
                        await conn.commit()
                except Exception:
                    logger.exception("Worker lost its database leadership connection")
                    if scheduler.running:
                        scheduler.shutdown(wait=False)
            monitor = asyncio.create_task(monitor_connection())
            try:
                yield scheduler
            finally:
                monitor.cancel()
                with suppress(asyncio.CancelledError):
                    await monitor
        finally:
            if scheduler is not None and scheduler.running:
                scheduler.shutdown(wait=False)
            await conn.execute(text("SELECT pg_advisory_unlock(70412026)"))
            await conn.commit()


async def main() -> None:
    while True:
        async with worker_lifespan() as scheduler:
            if scheduler is not None:
                logger.info("DMS worker ready")
                while scheduler.running:
                    await asyncio.sleep(10)
            else:
                await asyncio.sleep(30)


if __name__ == "__main__":
    asyncio.run(main())
