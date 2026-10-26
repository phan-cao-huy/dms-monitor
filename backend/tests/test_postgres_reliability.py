"""Opt-in PostgreSQL integration test; all objects/data are rolled back in a private schema."""

import asyncio
import os
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from app.core.config import settings
from app.db.base import Base
from app.models.alert import AlertHistory, AlertRule
from app.models.auth import User
from app.models.device import Device, DeviceCommand
from app.models.organization import Organization
from app.schemas.agent import CommandResultIn
from app.services.devices import (
    DeviceError,
    fetch_pending_commands,
    generate_device_token,
    record_command_result,
    verify_device_credentials,
)
from app.workers.rules import evaluate_device_rules


@pytest.mark.skipif(
    os.getenv("TEST_POSTGRES") != "1",
    reason="Requires PostgreSQL; uses private rollback schema",
)
def test_postgres_partition_rules_and_uninstall():
    asyncio.run(check_postgres())


async def check_postgres():
    engine = create_async_engine(settings.DATABASE_URL)
    schema = "dms_test_" + uuid.uuid4().hex
    now = datetime.now(UTC).replace(tzinfo=None)
    try:
        async with engine.connect() as conn:
            transaction = await conn.begin()
            try:
                await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                await conn.execute(
                    text(
                        "CREATE TABLE device_metrics (metric_id BIGSERIAL, device_id INT, cpu_usage FLOAT NOT NULL, ram_usage FLOAT NOT NULL, disk_usage FLOAT NOT NULL, net_in_kbps FLOAT DEFAULT 0, net_out_kbps FLOAT DEFAULT 0, created_at TIMESTAMP NOT NULL, PRIMARY KEY(metric_id, created_at)) PARTITION BY RANGE (created_at)"
                    )
                )
                await conn.execute(
                    text(
                        "CREATE TABLE device_metrics_default PARTITION OF device_metrics DEFAULT"
                    )
                )
                await conn.run_sync(Base.metadata.create_all)
                await conn.execute(
                    text(
                        Path(__file__)
                        .resolve()
                        .parents[1]
                        .joinpath("app/db/partition_function.sql")
                        .read_text()
                    )
                )
                for moment in (now, now + timedelta(days=7)):
                    await conn.execute(
                        text(
                            "INSERT INTO device_metrics (device_id,cpu_usage,ram_usage,disk_usage,created_at) VALUES (1,95,20,30,:t)"
                        ),
                        {"t": moment},
                    )
                    await conn.execute(
                        text("SELECT create_metrics_partition(:d)"),
                        {"d": moment.date()},
                    )
                    await conn.execute(
                        text("SELECT create_metrics_partition(:d)"),
                        {"d": moment.date()},
                    )
                assert (
                    await conn.execute(text("SELECT count(*) FROM device_metrics"))
                ).scalar() == 2
                assert (
                    await conn.execute(
                        text("SELECT count(*) FROM device_metrics_default")
                    )
                ).scalar() == 0

                async with AsyncSession(bind=conn, expire_on_commit=False) as db:
                    user = User(
                        username="test", email="test@example.com", password_hash="hash"
                    )
                    db.add(user)
                    await db.flush()
                    org = Organization(org_name="test", owner_id=user.user_id)
                    db.add(org)
                    await db.flush()
                    raw, hashed, prefix = generate_device_token()
                    device = Device(
                        org_id=org.org_id,
                        device_name="test",
                        secret_token_hash=hashed,
                        secret_token_prefix=prefix,
                        last_heartbeat=now,
                        status="Online",
                        is_monitoring=True,
                    )
                    db.add(device)
                    await db.flush()
                    rule = AlertRule(
                        device_id=device.device_id,
                        metric_type="CPU",
                        condition=">",
                        threshold=90,
                        duration_minutes=1,
                        cooldown_minutes=30,
                        severity="WARNING",
                    )
                    db.add(rule)
                    await db.flush()
                    await evaluate_device_rules(db, device, [rule], now)
                    await db.flush()
                    await evaluate_device_rules(
                        db, device, [rule], now + timedelta(seconds=61)
                    )
                    await db.flush()
                    alerts = list(
                        (await db.execute(select(AlertHistory))).scalars().all()
                    )
                    assert len(alerts) == 1
                    await evaluate_device_rules(
                        db, device, [rule], now + timedelta(seconds=62)
                    )
                    await db.flush()
                    assert (
                        len(
                            list(
                                (await db.execute(select(AlertHistory))).scalars().all()
                            )
                        )
                        == 1
                    )

                    from contextlib import asynccontextmanager

                    # Recover, then breach again inside cooldown: no duplicate alert.
                    await conn.execute(
                        text(
                            "UPDATE device_metrics SET cpu_usage=20 WHERE created_at <= :t"
                        ),
                        {"t": now},
                    )
                    await evaluate_device_rules(
                        db, device, [rule], now + timedelta(seconds=63)
                    )
                    await db.flush()
                    assert (
                        await db.execute(select(AlertHistory))
                    ).scalar_one().resolved_at is not None
                    await conn.execute(
                        text(
                            "UPDATE device_metrics SET cpu_usage=95,created_at=:t WHERE created_at <= :previous"
                        ),
                        {"t": now + timedelta(seconds=90), "previous": now},
                    )
                    await evaluate_device_rules(
                        db, device, [rule], now + timedelta(seconds=90)
                    )
                    await evaluate_device_rules(
                        db, device, [rule], now + timedelta(seconds=151)
                    )
                    await db.flush()
                    assert (
                        len(
                            list(
                                (await db.execute(select(AlertHistory))).scalars().all()
                            )
                        )
                        == 1
                    )

                    from unittest.mock import patch

                    from app.models.device import DeviceMetric, DeviceMetricHourly
                    from app.workers.runner import cleanup_metrics

                    old = now - timedelta(days=100)
                    db.add(
                        DeviceMetric(
                            device_id=device.device_id,
                            cpu_usage=1,
                            ram_usage=1,
                            disk_usage=1,
                            created_at=old,
                        )
                    )
                    db.add(
                        DeviceMetricHourly(device_id=device.device_id, hour_bucket=old)
                    )
                    await db.flush()

                    @asynccontextmanager
                    async def bound_session():
                        yield db

                    with patch("app.workers.runner.SessionLocal", bound_session):
                        await cleanup_metrics()
                    assert (
                        await db.execute(
                            select(DeviceMetric).where(DeviceMetric.created_at == old)
                        )
                    ).scalar_one_or_none() is None
                    assert (
                        await db.execute(select(DeviceMetricHourly))
                    ).scalar_one_or_none() is None
                    assert (
                        await conn.execute(text("SELECT count(*) FROM device_metrics"))
                    ).scalar() == 2

                    device.archived_at = now
                    device.status = "Archived"
                    cmd = DeviceCommand(
                        device_id=device.device_id,
                        created_by=user.user_id,
                        command_type="UNINSTALL",
                        status="PENDING",
                        reason="test",
                    )
                    db.add(cmd)
                    await db.flush()
                    assert await verify_device_credentials(db, raw) is device
                    assert await fetch_pending_commands(db, device) == [cmd]
                    await record_command_result(
                        db, device, cmd.command_id, CommandResultIn(status="SUCCESS")
                    )
                    # A bounded replay window recovers a lost HTTP acknowledgement.
                    assert await verify_device_credentials(db, raw) is device
                    assert await fetch_pending_commands(db, device) == [cmd]
                    completed = cmd.completed_at
                    await record_command_result(
                        db, device, cmd.command_id, CommandResultIn(status="SUCCESS")
                    )
                    assert cmd.completed_at == completed
                    cmd.completed_at = now - timedelta(minutes=6)
                    await db.flush()
                    with pytest.raises(DeviceError):
                        await verify_device_credentials(db, raw)
            finally:
                await transaction.rollback()
    finally:
        await engine.dispose()
