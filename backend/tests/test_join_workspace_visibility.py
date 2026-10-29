import asyncio
import os
import uuid
import pytest


@pytest.mark.skipif(os.getenv("TEST_POSTGRES")!="1",reason="Private rollback PostgreSQL schema")
def test_joined_viewer_reads_members_and_only_authorized_devices():
    from app.core.config import settings
    from app.db.base import Base
    from app.models.auth import User
    from app.models.device import Device
    from app.models.organization import Organization,OrganizationMember,OrganizationInvitation,DeviceGroup,UserGroupAccess
    from app.services.organizations import join_organization,list_members,list_organizations
    from app.services.devices import list_devices
    from sqlalchemy import text
    from sqlalchemy.ext.asyncio import create_async_engine,AsyncSession
    async def run():
        engine=create_async_engine(settings.DATABASE_URL)
        try:
            async with engine.connect() as conn:
                tx=await conn.begin()
                try:
                    schema="join_test_"+uuid.uuid4().hex
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db=AsyncSession(bind=conn,expire_on_commit=False)
                    owner=User(username="inviter",email="invite@example.com",password_hash="test")
                    guest=User(username="guest",email="guest@example.com",password_hash="test")
                    db.add_all([owner,guest]);await db.flush()
                    shared=Organization(org_name="Shared",owner_id=owner.user_id)
                    personal=Organization(org_name="Personal",owner_id=guest.user_id)
                    db.add_all([shared,personal]);await db.flush()
                    db.add_all([OrganizationMember(org_id=shared.org_id,user_id=owner.user_id,role="Owner"),
                        OrganizationMember(org_id=personal.org_id,user_id=guest.user_id,role="Owner"),
                        OrganizationInvitation(org_id=shared.org_id,invite_code="ABCDEF",default_role="Viewer",created_by=owner.user_id)])
                    group=DeviceGroup(org_id=shared.org_id,group_name="Restricted")
                    db.add(group);await db.flush()
                    free=Device(org_id=shared.org_id,device_name="Shared PC",secret_token_hash="free",secret_token_prefix="free")
                    restricted=Device(org_id=shared.org_id,group_id=group.group_id,device_name="Restricted PC",secret_token_hash="restricted",secret_token_prefix="restr")
                    private=Device(org_id=personal.org_id,device_name="Personal PC",secret_token_hash="private",secret_token_prefix="private")
                    db.add_all([free,restricted,private]);await db.flush()
                    joined=await join_organization(db,guest.user_id,"ABCDEF")
                    assert joined["org_id"]==shared.org_id and joined["role"]=="Viewer"
                    assert len(await list_organizations(db,guest.user_id))==2
                    assert {m["user_id"] for m in await list_members(db,shared.org_id,guest.user_id)}=={owner.user_id,guest.user_id}
                    assert {d["device_id"] for d in await list_devices(db,guest.user_id,shared.org_id)}=={free.device_id}
                    db.add(UserGroupAccess(user_id=guest.user_id,group_id=group.group_id,role="Viewer"));await db.flush()
                    assert {d["device_id"] for d in await list_devices(db,guest.user_id,shared.org_id)}=={free.device_id,restricted.device_id}
                    await db.close()
                finally:await tx.rollback()
        finally:await engine.dispose()
    asyncio.run(run())
