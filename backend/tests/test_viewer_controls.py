import asyncio
from unittest.mock import AsyncMock,Mock

import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.device import Device
from app.models.organization import OrganizationMember,UserGroupAccess
from app.services.devices import check_user_device_access,DeviceError


def session(role="Viewer",group_role="Manager"):
    device=Device(device_id=1,org_id=2,group_id=3)
    member=OrganizationMember(user_id=4,org_id=2,role=role)
    access=UserGroupAccess(user_id=4,group_id=3,role=group_role)
    async def execute(stmt):
        sql=str(stmt)
        obj=device if "FROM devices" in sql else member if "FROM org_members" in sql else access
        result=Mock();result.scalar_one_or_none.return_value=obj
        return result
    return Mock(execute=AsyncMock(side_effect=execute),commit=AsyncMock(),add=Mock()),device


@pytest.mark.parametrize("kind,params",[
    ("KILL_PROCESS",{"pid":12345}),
    ("KILL_PROCESS",{"process_name":"test.exe"}),
    ("REBOOT",{}),("RESTART_SERVICE",{"service_name":"Test"}),
    ("PAUSE_MONITORING",{}),("RESUME_MONITORING",{}),("UNINSTALL",{}),
])
def test_viewer_group_manager_cannot_queue_commands_via_direct_api(kind,params):
    db,_=session()
    async def user():return User(user_id=4)
    async def database():yield db
    app.dependency_overrides[get_current_user]=user
    app.dependency_overrides[get_db]=database
    try:
        response=TestClient(app).post("/api/v1/devices/1/commands",json={"command_type":kind,"parameters":params,"reason":"Permission regression; never execute"})
        assert response.status_code==403,response.text
        db.add.assert_not_called();db.commit.assert_not_awaited()
    finally:
        app.dependency_overrides.pop(get_current_user,None)
        app.dependency_overrides.pop(get_db,None)


@pytest.mark.parametrize("method,path,body",[
    ("PATCH","/api/v1/devices/1",{"group_id":0}),
    ("POST","/api/v1/devices/1/toggle-monitoring",{"is_monitoring":False}),
    ("DELETE","/api/v1/devices/1",None),
    ("PUT","/api/v1/devices/1/labels/env",{"value":"prod"}),
])
def test_viewer_is_read_only_on_device_mutations(method,path,body):
    db,_=session()
    async def user():return User(user_id=4)
    async def database():yield db
    app.dependency_overrides[get_current_user]=user
    app.dependency_overrides[get_db]=database
    try:
        response=TestClient(app).request(method,path,json=body)
        assert response.status_code==403,response.text
        db.add.assert_not_called();db.commit.assert_not_awaited()
    finally:
        app.dependency_overrides.pop(get_current_user,None)
        app.dependency_overrides.pop(get_db,None)


@pytest.mark.parametrize("workspace,group,allowed",[("Owner","Viewer",True),("Admin","Viewer",True),("Manager","Manager",True),("Manager","Viewer",False),("Viewer","Manager",False)])
def test_role_matrix_and_viewer_read_access(workspace,group,allowed):
    db,device=session(workspace,group)
    assert asyncio.run(check_user_device_access(db,1,4))[0]==device
    if allowed:
        assert asyncio.run(check_user_device_access(db,1,4,["Owner","Admin","Manager"]))[0]==device
    else:
        with pytest.raises(DeviceError) as error:asyncio.run(check_user_device_access(db,1,4,["Owner","Admin","Manager"]))
        assert error.value.status_code==403
