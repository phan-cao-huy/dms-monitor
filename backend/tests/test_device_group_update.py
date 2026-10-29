import asyncio
import os
import uuid
from datetime import UTC,datetime
from unittest.mock import AsyncMock,patch

import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.device import Device
from app.models.organization import Organization,OrganizationMember,DeviceGroup
from app.schemas.device import DeviceUpdate
from app.services.devices import update_device,DeviceError


def test_device_patch_requires_only_json_body_not_unrelated_query_parameter():
    now=datetime.now(UTC).replace(tzinfo=None)
    result=dict(device_id=1,org_id=1,group_id=2,device_name="Test PC",description=None,
        secret_token_prefix="test",status="Offline",is_monitoring=True,last_heartbeat=None,
        offline_threshold_minutes=5,created_at=now,updated_at=now)
    async def user():return User(user_id=1)
    async def db():yield object()
    app.dependency_overrides[get_current_user]=user
    app.dependency_overrides[get_db]=db
    try:
        with patch("app.api.v1.routers.devices.update_device",new=AsyncMock(return_value=result)) as update:
            response=TestClient(app).patch("/api/v1/devices/1",json={"group_id":2})
            assert response.status_code==200,response.text
            assert response.json()["group_id"]==2
            assert update.call_args.args[3].group_id==2
        parameters=app.openapi()["paths"]["/api/v1/devices/{device_id}"]["patch"].get("parameters",[])
        assert not any(p["name"]=="ToggleMonitoringIn" for p in parameters)
    finally:
        app.dependency_overrides.pop(get_current_user,None)
        app.dependency_overrides.pop(get_db,None)


@pytest.mark.skipif(os.getenv("TEST_POSTGRES")!="1",reason="Private rollback PostgreSQL schema")
def test_group_assignment_move_removal_and_scope_persist():
    from app.core.config import settings
    from app.db.base import Base
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import create_async_engine,AsyncSession
    async def run():
        engine=create_async_engine(settings.DATABASE_URL)
        try:
            async with engine.connect() as conn:
                tx=await conn.begin()
                try:
                    schema="group_test_"+uuid.uuid4().hex
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db=AsyncSession(bind=conn,expire_on_commit=False)
                    owner=User(username="group_owner",email="group@example.com",password_hash="test")
                    viewer=User(username="group_viewer",email="viewer@example.com",password_hash="test")
                    db.add_all([owner,viewer]);await db.flush()
                    org=Organization(org_name="Group test",owner_id=owner.user_id)
                    other=Organization(org_name="Other",owner_id=owner.user_id)
                    db.add_all([org,other]);await db.flush()
                    db.add_all([OrganizationMember(org_id=org.org_id,user_id=owner.user_id,role="Owner"),
                        OrganizationMember(org_id=org.org_id,user_id=viewer.user_id,role="Viewer")])
                    a=DeviceGroup(org_id=org.org_id,group_name="A")
                    b=DeviceGroup(org_id=org.org_id,group_name="B")
                    foreign=DeviceGroup(org_id=other.org_id,group_name="Other")
                    device=Device(org_id=org.org_id,device_name="Test PC",secret_token_hash="test",secret_token_prefix="test")
                    db.add_all([a,b,foreign,device]);await db.flush()
                    for group in (a,b):
                        await update_device(db,device.device_id,owner.user_id,DeviceUpdate(group_id=group.group_id))
                        await db.refresh(device)
                        assert device.group_id==group.group_id
                    await update_device(db,device.device_id,owner.user_id,DeviceUpdate(device_name="Renamed"))
                    assert device.group_id==b.group_id
                    with pytest.raises(DeviceError) as error:
                        await update_device(db,device.device_id,owner.user_id,DeviceUpdate(group_id=foreign.group_id))
                    assert error.value.status_code==404 and device.group_id==b.group_id
                    for removal in (0,None):
                        await update_device(db,device.device_id,owner.user_id,DeviceUpdate(group_id=a.group_id))
                        await update_device(db,device.device_id,owner.user_id,DeviceUpdate(group_id=removal))
                        await db.refresh(device)
                        assert device.group_id is None
                    with pytest.raises(DeviceError) as error:
                        await update_device(db,device.device_id,viewer.user_id,DeviceUpdate(group_id=a.group_id))
                    assert error.value.status_code==403 and device.group_id is None
                    await db.close()
                finally:await tx.rollback()
        finally:await engine.dispose()
    asyncio.run(run())
