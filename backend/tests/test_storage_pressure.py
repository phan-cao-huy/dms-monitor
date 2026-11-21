import pytest
from app.services.storage_health import storage_level


@pytest.mark.parametrize(("percent","level"), [(0,"normal"),(79.99,"normal"),(80,"warning"),(89.99,"warning"),(90,"critical"),(100,"critical")])
def test_disk_threshold(percent, level):
    assert storage_level(percent) == level

def test_disk_push_transitions_without_sending_to_phones():
    import asyncio
    import os
    from contextlib import asynccontextmanager
    from types import SimpleNamespace
    from unittest.mock import Mock, patch
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
    from app.core.config import settings
    from app.workers import storage_pressure as worker

    if os.getenv("TEST_POSTGRES") != "1":
        pytest.skip("Requires PostgreSQL")
    async def run():
        engine = create_async_engine(settings.DATABASE_URL)
        try:
            async with engine.begin() as conn:
                for stmt in (
                    "CREATE TEMP TABLE organizations(org_id int,deleted_at timestamp) ON COMMIT DROP",
                    "CREATE TEMP TABLE org_members(org_id int,user_id int,role text) ON COMMIT DROP",
                    "CREATE TEMP TABLE auth_tokens(user_id int,fcm_token text,token_type text,used_at timestamp,expires_at timestamp) ON COMMIT DROP",
                    "CREATE TEMP TABLE storage_push_state(user_id int primary key,level text,sent_at timestamptz default now()) ON COMMIT DROP",
                    "INSERT INTO organizations VALUES(1,NULL)",
                    "INSERT INTO org_members VALUES(1,1,'Admin'),(1,2,'Viewer')",
                    "INSERT INTO auth_tokens VALUES(1,'admin-fixture','REFRESH',NULL,now()+interval '1 day'),(2,'viewer-fixture','REFRESH',NULL,now()+interval '1 day')",
                ):
                    await conn.execute(text(stmt))
                @asynccontextmanager
                async def session():
                    async with AsyncSession(bind=conn,join_transaction_mode="create_savepoint") as db:
                        yield db
                sender=Mock(return_value=SimpleNamespace(success_count=1))
                with patch.object(worker,'SessionLocal',session), patch.object(worker,'firebase_app'), patch.object(settings,'FCM_ENABLED',True), patch.object(settings,'FCM_CREDENTIALS_FILE','fixture'), patch('firebase_admin.messaging.send_each_for_multicast',sender):
                    for percent in [80,80,90,70,80]:
                        with patch.object(worker,'disk_snapshot',return_value=dict(disk_level=storage_level(percent),disk_percent=percent)):
                            await worker.notify_disk_pressure()
                    assert sender.call_count == 3
                    assert all(call.args[0].tokens == ['admin-fixture'] for call in sender.call_args_list)
        finally:
            await engine.dispose()
    asyncio.run(run())
