import asyncio
import os
from contextlib import asynccontextmanager
from unittest.mock import patch

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from app.core.config import settings
from app.workers import alert_retention


@pytest.mark.skipif(os.getenv("TEST_POSTGRES") != "1", reason="Requires PostgreSQL")
def test_only_expired_resolved_alerts_in_opted_in_workspace_are_removed():
    asyncio.run(check_retention())


async def check_retention():
    engine = create_async_engine(settings.DATABASE_URL)
    try:
        async with engine.begin() as conn:
            for stmt in (
                "CREATE TEMP TABLE workspace_alert_retention(org_id int, days int) ON COMMIT DROP",
                "CREATE TEMP TABLE devices(device_id int, org_id int) ON COMMIT DROP",
                "CREATE TEMP TABLE alert_history(alert_id int, device_id int, resolved_at timestamp) ON COMMIT DROP",
                "INSERT INTO devices VALUES (1,1),(2,2)",
                """INSERT INTO alert_history VALUES (1,1,now()-interval '31 days'),(2,1,NULL),
                   (3,1,now()-interval '2 days'),(4,2,now()-interval '90 days')""",
            ):
                await conn.execute(text(stmt))
            @asynccontextmanager
            async def session():
                async with AsyncSession(bind=conn, join_transaction_mode="create_savepoint") as db:
                    yield db
            with patch.object(alert_retention, "SessionLocal", session):
                await alert_retention.prune_resolved_alerts()
                assert await conn.scalar(text("SELECT count(*) FROM alert_history")) == 4
                await conn.execute(text("INSERT INTO workspace_alert_retention VALUES (1,30)"))
                await alert_retention.prune_resolved_alerts()
                assert list(await conn.scalars(text("SELECT alert_id FROM alert_history ORDER BY alert_id"))) == [2,3,4]
    finally:
        await engine.dispose()
