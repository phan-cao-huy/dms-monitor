"""Background workers (APScheduler). Chay rieng: python -m app.workers.runner

Jobs can lam (theo schema.sql):
- offline_detector   : moi 1 phut, qua offline_threshold_minutes -> status='Offline' + tao alert
- command_timeout    : moi 1 phut, PENDING/EXECUTING qua command_timeout_minutes -> 'TIMEOUT'
- partition_manager  : moi Chu Nhat, create_metrics_partition() + DROP partition > 7 ngay
- hourly_aggregator  : moi gio, device_metrics -> device_metrics_hourly
- retention_cleaner  : process_snapshots (24h), commands (90d), hourly (90d)
- alert_evaluator    : danh gia alert_rules, gui FCM
"""
import asyncio


async def main():
    # TODO(Dev1): AsyncIOScheduler + add_job cho cac job tren
    while True:
        await asyncio.sleep(3600)


if __name__ == "__main__":
    asyncio.run(main())
