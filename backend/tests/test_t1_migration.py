import asyncio
import os
import uuid
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from pathlib import Path
from unittest.mock import patch

import pytest
from app.core.config import settings
from app.db.base import Base
from app.models.alert import AlertHistory, AlertRule
from app.models.auth import User
from app.models.device import Device
from app.models.email_channel import EmailChannel, EmailDelivery
from app.models.organization import Organization
from app.services.default_rules import seed_default_rules
from app.services.email_channels import encrypt_config
from app.workers import email_notifications
from cryptography.fernet import Fernet
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine


def test_migration_copies_stay_in_sync():
    root = Path(__file__).resolve().parents[2]
    if not (root / "database").exists():
        pytest.skip("Repository-level copies are checked on host, outside backend-only image")
    migration = (root / "database/migrate_t1_email.sql").read_text(encoding="utf-8")
    assert migration == (root / "backend/app/db/t1_email.sql").read_text(encoding="utf-8")
    assert (root / "database/dms01.sql").read_text(encoding="utf-8").endswith(migration)


@pytest.mark.skipif(os.getenv("TEST_POSTGRES") != "1", reason="Requires PostgreSQL; private rollback schema")
def test_postgres_t1_migration_and_seed_are_idempotent():
    async def run():
        engine = create_async_engine(settings.DATABASE_URL)
        schema = "t1_test_" + uuid.uuid4().hex
        try:
            async with engine.connect() as conn:
                transaction = await conn.begin()
                try:
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db = AsyncSession(bind=conn, expire_on_commit=False)
                    user = User(username="t1_owner", email="t1@example.com", password_hash="test")
                    db.add(user)
                    await db.flush()
                    org = Organization(org_name="T1", owner_id=user.user_id)
                    db.add(org)
                    await db.flush()
                    sql = Path(__file__).resolve().parents[1].joinpath("app/db/t1_email.sql").read_text(encoding="utf-8")
                    for _ in range(2):
                        for stmt in sql.split(";"):
                            if stmt.strip():
                                await db.execute(text(stmt))
                        await seed_default_rules(db, org.org_id)
                    assert await db.scalar(select(func.count()).select_from(AlertRule)) == 5
                    rule = (await db.execute(select(AlertRule).where(AlertRule.metric_type == "RAM"))).scalar_one()
                    rule.is_active = False
                    rule.threshold = 75
                    await db.flush()
                    await seed_default_rules(db, org.org_id)
                    await db.refresh(rule)
                    assert rule.is_active is False and rule.threshold == 75

                    device = Device(org_id=org.org_id, device_name="Test PC", secret_token_hash="testhash",
                                    secret_token_prefix="test", is_monitoring=True)
                    db.add(device)
                    await db.flush()
                    config = {"host":"smtp.gmail.com", "port":587, "username":"test@example.com",
                              "password":"test", "from_email":"test@example.com",
                              "recipients":["receiver@example.com"], "language":"vi"}
                    with patch("app.services.email_channels.settings.NOTIFICATION_ENCRYPTION_KEY", Fernet.generate_key().decode()):
                        channel = EmailChannel(org_id=org.org_id, name="Test mail", enabled=True,
                                               config_encrypted=encrypt_config(config))
                        db.add(channel)
                        await db.flush()
                        alert = AlertHistory(device_id=device.device_id, severity="WARNING", message="RAM alert\n95%",
                                             notification_status="SENT", created_at=datetime.now(UTC).replace(tzinfo=None))
                        db.add(alert)
                        await db.flush()

                        @asynccontextmanager
                        async def session():
                            yield db

                        with patch.object(email_notifications, "SessionLocal", session), patch.object(email_notifications, "send_email") as smtp:
                            await email_notifications.send_pending_emails()
                            await email_notifications.send_pending_emails()
                        smtp.assert_called_once()
                        deliveries = (await db.execute(select(EmailDelivery))).scalars().all()
                        assert len(deliveries) == 1 and deliveries[0].status == "SENT"
                        assert alert.notification_status == "SENT"
                    await db.close()
                finally:
                    await transaction.rollback()
        finally:
            await engine.dispose()
    asyncio.run(run())
