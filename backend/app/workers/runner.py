"""Background workers (APScheduler). Chạy riêng: python -m app.workers.runner

Các tác vụ định kỳ:
- offline_detector   : mỗi 1 phút, nếu quá offline_threshold_minutes -> status='Offline' + ghi alert
- command_timeout    : mỗi 1 phút, lệnh PENDING/EXECUTING quá 5 phút -> TIMEOUT
- partition_manager  : mỗi ngày/Chủ nhật, gọi create_metrics_partition()
- hourly_aggregator  : mỗi giờ, tổng hợp device_metrics -> device_metrics_hourly
"""

import asyncio
import logging
from datetime import UTC, datetime, timedelta

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import func, select, text

from app.db.session import SessionLocal
from app.models.alert import AlertHistory
from app.models.device import Device, DeviceCommand, DeviceMetric, DeviceMetricHourly

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] (Worker) %(message)s",
)
logger = logging.getLogger("worker")


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


async def check_command_timeouts() -> None:
    now = _utc_naive()
    timeout_threshold = now - timedelta(minutes=5)
    async with SessionLocal() as db:
        try:
            stmt = select(DeviceCommand).where(
                DeviceCommand.status.in_(("PENDING", "EXECUTING")),
                DeviceCommand.created_at < timeout_threshold,
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
    async with SessionLocal() as db:
        try:
            today = _utc_naive().date()
            res = await db.execute(
                text("SELECT create_metrics_partition(:d);"), {"d": today}
            )
            msg = res.scalar()
            logger.info(f"Partition manager: {msg}")
        except Exception:
            logger.exception("Lỗi partition manager")


async def main() -> None:
    logger.info("Khởi động DMS Background Workers...")
    scheduler = AsyncIOScheduler()

    scheduler.add_job(check_offline_devices, "interval", minutes=1)
    scheduler.add_job(check_command_timeouts, "interval", minutes=1)
    scheduler.add_job(aggregate_hourly_metrics, "cron", minute=5)
    scheduler.add_job(manage_partitions, "cron", day_of_week="sun", hour=0, minute=0)

    scheduler.start()
    logger.info("DMS Background Workers đã sẵn sàng.")

    try:
        while True:
            await asyncio.sleep(3600)
    except (KeyboardInterrupt, SystemExit):
        scheduler.shutdown()
        logger.info("DMS Background Workers đã dừng.")


if __name__ == "__main__":
    asyncio.run(main())
