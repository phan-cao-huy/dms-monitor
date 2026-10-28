import asyncio
import os
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path

import httpx
import pytest
from app.core.config import settings
from app.core.deps import get_current_user
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models.auth import User
from app.models.device import Device, DeviceMetric, DeviceMetricHourly
from app.models.metrics import DeviceMetricMinute
from app.models.organization import (
    DeviceGroup,
    Organization,
    OrganizationMember,
    UserGroupAccess,
)
from app.services.device_labels import parse_label, remove_label, set_label
from app.services.devices import DeviceError
from app.services.metric_history import device_series, resolution_for, step_for, window
from app.services.metrics_query import query_metrics
from app.workers.metric_rollups import rollup_window
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine


@pytest.mark.parametrize("hours,tier",[(1,"raw"),(6,"raw"),(6.01,"minute"),(72,"minute"),(73,"hourly"),(2160,"hourly")])
def test_resolution_boundaries(hours,tier):
    end=datetime.now(UTC)
    start=end-timedelta(hours=hours)
    assert resolution_for(start,end)==tier
    assert (end-start).total_seconds()/step_for(start,end,tier)<=500


@pytest.mark.parametrize("value",["bad","a');DROP:secret","A:value","key:"+"x"*65])
def test_label_filter_rejects_invalid_input(value):
    with pytest.raises(DeviceError):
        parse_label(value)


def test_invalid_time_range_rejected():
    end=datetime.now(UTC)
    with pytest.raises(DeviceError):
        window(end,end)
    with pytest.raises(DeviceError):
        window(end-timedelta(days=91),end)


def test_t3_migration_copies_match():
    root=Path(__file__).resolve().parents[2]
    if not (root/'database').exists():
        pytest.skip('Repository copies checked on host')
    sql=(root/'database/migrate_t3_metrics.sql').read_text(encoding='utf-8')
    assert sql==(root/'backend/app/db/t3_metrics.sql').read_text(encoding='utf-8')
    assert (root/'database/dms01.sql').read_text(encoding='utf-8').endswith(sql)


@pytest.mark.skipif(os.getenv('TEST_POSTGRES')!='1',reason='Private rollback schema requires PostgreSQL')
def test_t3_weighted_rollups_scoped_queries_and_label_limit():
    async def run():
        engine=create_async_engine(settings.DATABASE_URL)
        schema='t3_test_'+uuid.uuid4().hex
        now=datetime.now(UTC).replace(tzinfo=None)
        t0=(now-timedelta(hours=5)).replace(minute=0,second=0,microsecond=0)
        try:
            async with engine.connect() as conn:
                tx=await conn.begin()
                try:
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db=AsyncSession(bind=conn,expire_on_commit=False)
                    sql=Path(__file__).resolve().parents[1].joinpath('app/db/t3_metrics.sql').read_text(encoding='utf-8')
                    for _ in range(2):
                        for stmt in sql.split(';'):
                            if stmt.strip():
                                await db.execute(text(stmt))
                    owner=User(username='owner',email='owner@test.com',password_hash='test')
                    viewer=User(username='viewer',email='viewer@test.com',password_hash='test')
                    db.add_all([owner,viewer]);await db.flush()
                    org=Organization(org_name='T3',owner_id=owner.user_id)
                    db.add(org);await db.flush()
                    group=DeviceGroup(org_id=org.org_id,group_name='Allowed')
                    hidden_group=DeviceGroup(org_id=org.org_id,group_name='HIDDEN')
                    db.add_all([group,hidden_group]);await db.flush()
                    db.add_all([OrganizationMember(org_id=org.org_id,user_id=owner.user_id,role='Owner'),
                                OrganizationMember(org_id=org.org_id,user_id=viewer.user_id,role='Viewer'),
                                UserGroupAccess(user_id=viewer.user_id,group_id=group.group_id,role='Viewer')])
                    devices=[Device(org_id=org.org_id,group_id=g,device_name=n,secret_token_hash=n,secret_token_prefix='test') for g,n in [(group.group_id,'First'),(group.group_id,'Second'),(hidden_group.group_id,'SECRET')]]
                    db.add_all(devices);await db.flush()
                    for index,(device,cpu,offset,gpu,temp) in enumerate([(devices[0],0,10,0,40),(devices[0],100,20,20,60),(devices[1],100,10,None,None),(devices[2],90,10,90,90)]):
                        db.add(DeviceMetric(device_id=device.device_id,cpu_usage=cpu,ram_usage=cpu,disk_usage=30,
                            net_in_kbps=10,net_out_kbps=20,gpu_usage_pct=gpu,cpu_temp_c=temp,created_at=t0+timedelta(seconds=offset)))
                    await db.flush()
                    result=await query_metrics(db,org.org_id,viewer.user_id,'cpu_usage','avg','group',t0,t0+timedelta(minutes=1),60)
                    assert len(result['series'])==1 and result['series'][0]['name']=='Allowed'
                    assert result['series'][0]['points'][0]['value']==pytest.approx(200/3)
                    await rollup_window(db,t0,t0+timedelta(hours=1),now)
                    await rollup_window(db,t0,t0+timedelta(hours=1),now)
                    assert await db.scalar(select(func.count()).select_from(DeviceMetricMinute))==3
                    for hours,tier in [(7,'minute'),(96,'hourly')]:
                        result=await query_metrics(db,org.org_id,viewer.user_id,'cpu_usage','avg','group',t0-timedelta(hours=hours-1),t0+timedelta(hours=1),3600)
                        assert result['resolution']==tier and len(result['series'])==1
                        assert result['series'][0]['points'][0]['value']==pytest.approx(200/3)
                        assert result['series'][0]['points'][0]['sample_count']==3
                    for agg,expected in [('avg',10),('min',0),('max',20)]:
                        result=await query_metrics(db,org.org_id,viewer.user_id,'gpu_usage_pct',agg,'group',t0-timedelta(hours=95),t0+timedelta(hours=1),3600)
                        assert result['series'][0]['points'][0]['value']==expected
                    await set_label(db,devices[0].device_id,owner.user_id,'site','missing')
                    result=await query_metrics(db,org.org_id,viewer.user_id,'cpu_usage','avg','label:site',t0,t0+timedelta(minutes=1),60)
                    assert len(result['series'])==2
                    for i in range(19):
                        await set_label(db,devices[0].device_id,owner.user_id,f'key{i}','v')
                    with pytest.raises(DeviceError):
                        await set_label(db,devices[0].device_id,owner.user_id,'overflow','v')
                    await set_label(db,devices[0].device_id,owner.user_id,'site','prod')
                    with pytest.raises(DeviceError):
                        await set_label(db,devices[0].device_id,viewer.user_id,'site','bad')
                    result=await query_metrics(db,org.org_id,viewer.user_id,'cpu_usage','avg','device',t0,t0+timedelta(minutes=1),60,'site:prod')
                    assert len(result['series'])==1 and result['series'][0]['name']=='First'
                    await remove_label(db,devices[0].device_id,owner.user_id,'site')
                    legacy=t0-timedelta(days=8)
                    db.add(DeviceMetricHourly(device_id=devices[0].device_id,hour_bucket=legacy,cpu_avg=50,cpu_max=90,ram_avg=50,disk_avg=40,net_in_avg_kbps=1,net_out_avg_kbps=2,sample_count=10))
                    await db.flush()
                    result=await query_metrics(db,org.org_id,viewer.user_id,'cpu_usage','min','device',legacy,legacy+timedelta(days=4))
                    assert result['series'][0]['points'][0]['value'] is None
                    envelope=await device_series(db,devices[0].device_id,t0-timedelta(hours=6),t0+timedelta(hours=1),'minute')
                    assert len(envelope['items'])<=500 and envelope['items'][0]['cpu_usage']==50
                    auth={'user':viewer}
                    async def user_dependency():
                        return auth['user']
                    async def db_dependency():
                        yield db
                    app.dependency_overrides[get_current_user]=user_dependency
                    app.dependency_overrides[get_db]=db_dependency
                    try:
                        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://test') as client:
                            response=await client.get(f'/api/v1/orgs/{org.org_id}/metrics/query',params={'metric':'cpu_usage;DROP'})
                            assert response.status_code==422
                            response=await client.get(f'/api/v1/orgs/{org.org_id}/metrics/query',params={'group_by':'label:bad);DROP'})
                            assert response.status_code==422
                            response=await client.put(f'/api/v1/devices/{devices[0].device_id}/labels/env',json={'value':'prod'})
                            assert response.status_code==403
                            auth['user']=owner
                            response=await client.put(f'/api/v1/devices/{devices[0].device_id}/labels/env',json={'value':'prod'})
                            assert response.status_code==200
                            response=await client.get(f'/api/v1/orgs/{org.org_id}/devices',params={'label':'env:prod'})
                            assert response.status_code==200 and len(response.json())==1
                            response=await client.get(f'/api/v1/devices/{devices[0].device_id}')
                            assert response.status_code==200 and response.json()['labels']['env']=='prod'
                            dates={'from':t0.isoformat()+'Z','to':(t0+timedelta(hours=7)).isoformat()+'Z'}
                            response=await client.get(f'/api/v1/devices/{devices[0].device_id}/metrics',params=dates|{'format':'series','limit':500})
                            assert response.status_code==200 and response.json()['resolution']=='minute'
                            dates['to']=(t0+timedelta(hours=1)).isoformat()+'Z'
                            for interval,field in [('raw','cpu_usage'),('hourly','cpu_avg')]:
                                response=await client.get(f'/api/v1/devices/{devices[0].device_id}/metrics',params=dates|{'interval':interval})
                                assert response.status_code==200 and field in response.json()[0]
                            response=await client.delete(f'/api/v1/devices/{devices[0].device_id}/labels/env')
                            assert response.status_code==204
                    finally:
                        app.dependency_overrides.clear()
                    await db.close()
                finally:
                    await tx.rollback()
        finally:
            await engine.dispose()
    asyncio.run(run())
