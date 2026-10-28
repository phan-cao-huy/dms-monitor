import asyncio
import os
import uuid
from contextlib import asynccontextmanager
from datetime import UTC, datetime, timedelta
from pathlib import Path
from unittest.mock import AsyncMock, patch

import httpx
import pytest
from app.core.config import settings
from app.core.deps import get_current_agent_device, get_current_user
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models.auth import User
from app.models.device import Device
from app.models.email_channel import EmailChannel
from app.models.monitoring import (
    CheckEmailDelivery,
    CheckIncident,
    DeviceEvent,
    SyntheticCheck,
)
from app.models.organization import Organization, OrganizationMember
from app.schemas.monitoring import DeviceEventIn
from app.services.device_events import ingest_events, list_events
from app.services.email_channels import encrypt_config
from app.services.synthetic_checks import run_check
from app.workers import runner, synthetic_checks
from cryptography.fernet import Fernet
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine


def test_t2_migration_copies_match():
    root=Path(__file__).resolve().parents[2]
    if not (root/'database').exists():
        pytest.skip('Repository copies are checked on host')
    sql=(root/'database/migrate_t2_monitoring.sql').read_text(encoding='utf-8')
    assert sql==(root/'backend/app/db/t2_monitoring.sql').read_text(encoding='utf-8')
    assert sql in (root/'database/dms01.sql').read_text(encoding='utf-8')


@pytest.mark.skipif(os.getenv('TEST_POSTGRES')!='1',reason='Requires PostgreSQL; private rollback schema')
def test_t2_events_incidents_and_email_ledger():
    async def run():
        engine=create_async_engine(settings.DATABASE_URL)
        schema='t2_test_'+uuid.uuid4().hex
        try:
            async with engine.connect() as conn:
                tx=await conn.begin()
                try:
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db=AsyncSession(bind=conn,expire_on_commit=False)
                    sql=Path(__file__).resolve().parents[1].joinpath('app/db/t2_monitoring.sql').read_text(encoding='utf-8')
                    for _ in range(2):
                        for stmt in sql.split(';'):
                            if stmt.strip():
                                await db.execute(text(stmt))
                    user=User(username='t2_owner',email='t2@example.com',password_hash='test')
                    db.add(user)
                    await db.flush()
                    org=Organization(org_name='T2',owner_id=user.user_id)
                    db.add(org)
                    await db.flush()
                    db.add(OrganizationMember(org_id=org.org_id,user_id=user.user_id,role='Owner'))
                    device=Device(org_id=org.org_id,device_name='T2 PC',secret_token_hash='t2hash',secret_token_prefix='t2',is_monitoring=True)
                    db.add(device)
                    await db.flush()
                    event=DeviceEventIn(log_name='System',record_id=42,event_id=1000,level='ERROR',source='Test',message='Test event',occurred_at=datetime.now(UTC))
                    await ingest_events(db,device,[event,event])
                    await ingest_events(db,device,[event])
                    assert await db.scalar(select(func.count()).select_from(DeviceEvent))==1
                    page=await list_events(db,device.device_id,user.user_id,'ERROR',None,1)
                    assert len(page['items'])==1
                    page=await list_events(db,device.device_id,user.user_id,'CRITICAL',None,1)
                    assert not page['items']
                    async def agent_dependency():
                        return device
                    async def user_dependency():
                        return user
                    async def db_dependency():
                        yield db
                    app.dependency_overrides[get_current_agent_device]=agent_dependency
                    app.dependency_overrides[get_current_user]=user_dependency
                    app.dependency_overrides[get_db]=db_dependency
                    try:
                        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://test') as client:
                            response=await client.get(f'/api/v1/devices/{device.device_id}/events')
                            assert response.status_code==200 and len(response.json()['items'])==1
                            device.is_monitoring=False
                            response=await client.post('/api/v1/agent/events',json={'events':[event.model_dump(mode='json')]})
                            assert response.status_code==403
                            device.is_monitoring=True
                            with patch('app.api.v1.routers.synthetic_checks.check_target',AsyncMock()):
                                response=await client.post(f'/api/v1/orgs/{org.org_id}/checks',json={'name':'API test','type':'HTTP','target':'https://example.com','enabled':False})
                            assert response.status_code==201
                            check_id=response.json()['check_id']
                            response=await client.get(f'/api/v1/orgs/{org.org_id}/checks/{check_id}/history')
                            assert response.status_code==200 and response.json()['results']==[]
                            response=await client.delete(f'/api/v1/orgs/{org.org_id}/checks/{check_id}')
                            assert response.status_code==204
                    finally:
                        app.dependency_overrides.clear()
                    db.add(DeviceEvent(device_id=device.device_id, log_name='System', record_id=1, event_id=1,
                                       level='ERROR', source='Old', message='Expired', occurred_at=datetime.now(UTC).replace(tzinfo=None)-timedelta(days=settings.EVENTS_RETENTION_DAYS+1)))
                    await db.flush()
                    config={'host':'smtp.gmail.com','port':587,'username':'t2@example.com','password':'test',
                            'from_email':'t2@example.com','recipients':['receiver@example.com'],'language':'vi'}
                    with patch('app.services.email_channels.settings.NOTIFICATION_ENCRYPTION_KEY',Fernet.generate_key().decode()):
                        channel=EmailChannel(org_id=org.org_id,name='T2 mail',enabled=True,config_encrypted=encrypt_config(config))
                        check=SyntheticCheck(org_id=org.org_id,name='Test HTTP',type='HTTP',target='https://example.com',interval_seconds=30,timeout_seconds=5,failure_threshold=2,enabled=True)
                        db.add_all([channel,check])
                        await db.flush()
                        with patch('app.services.synthetic_checks.probe',AsyncMock(return_value=(False,5,'HTTP_500'))):
                            await run_check(db,check)
                            assert not check.incident_open
                            await run_check(db,check)
                            await run_check(db,check)
                        assert check.incident_open
                        assert await db.scalar(select(func.count()).select_from(CheckIncident))==1

                        @asynccontextmanager
                        async def session():
                            yield db

                        with patch.object(synthetic_checks,'SessionLocal',session),patch.object(synthetic_checks,'send_email') as smtp:
                            await synthetic_checks.send_check_emails()
                            await synthetic_checks.send_check_emails()
                        smtp.assert_called_once()
                        delivery=(await db.execute(select(CheckEmailDelivery))).scalar_one()
                        assert delivery.status=='SENT' and delivery.attempts==1
                        check.last_checked_at=datetime.now(UTC).replace(tzinfo=None)-timedelta(minutes=1)
                        with patch.object(synthetic_checks,'SessionLocal',session),patch('app.services.synthetic_checks.probe',AsyncMock(return_value=(True,5,'HTTP_200'))):
                            await synthetic_checks.run_synthetic_checks()
                        assert not check.incident_open
                        incident=(await db.execute(select(CheckIncident))).scalar_one()
                        assert incident.resolved_at is not None
                        with patch.object(runner, 'SessionLocal', session):
                            await runner.cleanup_metrics()
                        assert await db.scalar(select(func.count()).select_from(DeviceEvent))==1
                    await db.close()
                finally:
                    await tx.rollback()
        finally:
            await engine.dispose()
    asyncio.run(run())
