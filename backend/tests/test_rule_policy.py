import asyncio
import os
import uuid
from pathlib import Path
from types import SimpleNamespace

import pytest
from app.services.rule_selection import effective_rules


def rule(rule_id, metric="RAM", device=None, group=None, workspace=None, active=True, subject=None):
    return SimpleNamespace(rule_id=rule_id, metric_type=metric, device_id=device,
        group_id=group, workspace_id=workspace, is_active=active,
        service_name=subject, process_name=subject)


def test_only_one_rule_with_device_group_default_precedence():
    device=SimpleNamespace(device_id=1,group_id=2,org_id=3)
    rules=[rule(1,workspace=3),rule(2,group=2),rule(3,device=1),rule(4,device=1),
           rule(5,device=99),rule(6,workspace=99),rule(7,"CPU",workspace=3)]
    assert {r.rule_id for r in effective_rules(device,rules)}=={4,7}
    assert {r.rule_id for r in effective_rules(device,list(reversed(rules)))}=={4,7}
    rules[3].is_active=False
    assert {r.rule_id for r in effective_rules(device,rules)}=={3,7}
    rules[2].is_active=False
    assert {r.rule_id for r in effective_rules(device,rules)}=={2,7}
    rules[1].is_active=False
    assert {r.rule_id for r in effective_rules(device,rules)}=={1,7}


def test_distinct_named_services_are_independent_and_unassigned_device_has_no_group():
    device=SimpleNamespace(device_id=1,group_id=None,org_id=3)
    rules=[rule(1,"SERVICE",device=1,subject="Spooler"),
           rule(2,"SERVICE",device=1,subject="DMS-Agent"),rule(3,group=2),rule(4,workspace=3,active=False)]
    assert {r.rule_id for r in effective_rules(device,rules)}=={1,2}


@pytest.mark.skipif(os.getenv("TEST_POSTGRES")!="1",reason="Private rollback PostgreSQL schema")
def test_unique_migration_preserves_history_and_api_conflicts():
    from app.core.config import settings
    from app.db.base import Base
    from app.models.alert import AlertRule,AlertHistory
    from app.models.auth import User
    from app.models.device import Device
    from app.models.organization import Organization,OrganizationMember
    from app.schemas.alert import AlertRuleCreate,AlertRuleUpdate
    from app.services.alerts import create_alert_rule,update_alert_rule,list_alert_rules,AlertError
    from sqlalchemy import select,text
    from sqlalchemy.exc import IntegrityError
    from sqlalchemy.ext.asyncio import AsyncSession,create_async_engine

    async def run():
        engine=create_async_engine(settings.DATABASE_URL)
        try:
            async with engine.connect() as conn:
                tx=await conn.begin()
                try:
                    schema="rule_test_"+uuid.uuid4().hex
                    await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                    await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                    await conn.run_sync(Base.metadata.create_all)
                    db=AsyncSession(bind=conn,expire_on_commit=False)
                    user=User(username="rule_tester",email="rules@example.com",password_hash="test")
                    db.add(user);await db.flush()
                    org=Organization(org_name="Rule test",owner_id=user.user_id)
                    db.add(org);await db.flush()
                    db.add(OrganizationMember(org_id=org.org_id,user_id=user.user_id,role="Owner"))
                    device=Device(org_id=org.org_id,device_name="pc1",secret_token_hash="test",secret_token_prefix="test")
                    db.add(device);await db.flush()
                    old=AlertRule(device_id=device.device_id,metric_type="RAM",threshold=93,is_active=True)
                    new=AlertRule(device_id=device.device_id,metric_type="RAM",threshold=50,is_active=True)
                    db.add_all([old,new]);await db.flush()
                    history=AlertHistory(device_id=device.device_id,rule_id=old.rule_id,severity="WARNING",message="test")
                    db.add(history);await db.flush()
                    sql=Path(__file__).resolve().parents[1].joinpath("app/db/unique_rules.sql").read_text()
                    for _ in range(2):
                        for stmt in sql.split(";"):
                            if stmt.strip():await db.execute(text(stmt))
                    await db.refresh(old);await db.refresh(new);await db.refresh(history)
                    assert not old.is_active and new.is_active
                    assert history.rule_id==old.rule_id and history.resolved_at is not None
                    with pytest.raises(AlertError) as error:
                        await create_alert_rule(db,org.org_id,user.user_id,AlertRuleCreate(device_id=device.device_id,metric_type="RAM",threshold=70))
                    assert error.value.status_code==409
                    with pytest.raises(AlertError) as error:
                        await update_alert_rule(db,org.org_id,old.rule_id,user.user_id,AlertRuleUpdate(is_active=True))
                    assert error.value.status_code==409
                    # Database protection also catches concurrent/direct inserts.
                    with pytest.raises(IntegrityError):
                        async with db.begin_nested():
                            db.add(AlertRule(device_id=device.device_id,metric_type="RAM",threshold=80,is_active=True))
                            await db.flush()
                    assert (await db.execute(select(AlertRule).where(AlertRule.is_active.is_(True)))).scalars().all()==[new]
                    from datetime import datetime,UTC
                    archived=Device(org_id=org.org_id,device_name="pc1",secret_token_hash="old",secret_token_prefix="old",
                        archived_at=datetime.now(UTC).replace(tzinfo=None))
                    db.add(archived);await db.flush()
                    old_device_rule=AlertRule(device_id=archived.device_id,metric_type="RAM",threshold=60,is_active=True)
                    db.add(old_device_rule);await db.flush()
                    visible=await list_alert_rules(db,org.org_id,user.user_id)
                    assert old_device_rule.rule_id not in {r.rule_id for r in visible}
                    with pytest.raises(AlertError) as error:
                        await create_alert_rule(db,org.org_id,user.user_id,AlertRuleCreate(device_id=archived.device_id,metric_type="CPU",threshold=70))
                    assert error.value.status_code==404
                    # A lower device threshold must fire while inherited history is retired.
                    from contextlib import asynccontextmanager
                    from datetime import timedelta
                    from unittest.mock import patch
                    from app.models.alert import AlertRuleState
                    from app.models.device import DeviceMetric
                    from app.workers import rules as worker
                    now=datetime.now(UTC).replace(tzinfo=None)
                    device.last_heartbeat=now
                    device.is_monitoring=True
                    device.status="Online"
                    new.duration_minutes=1
                    inherited=AlertRule(workspace_id=org.org_id,default_key="RAM",metric_type="RAM",threshold=93,is_active=True)
                    db.add(inherited);await db.flush()
                    inherited_alert=AlertHistory(device_id=device.device_id,rule_id=inherited.rule_id,severity="WARNING",message="old inherited alert")
                    db.add_all([inherited_alert,
                        DeviceMetric(device_id=device.device_id,created_at=now,cpu_usage=10,ram_usage=70,disk_usage=20),
                        AlertRuleState(rule_id=new.rule_id,device_id=device.device_id,active_since=now-timedelta(minutes=2),last_seen=now-timedelta(seconds=30)),
                        AlertRuleState(rule_id=inherited.rule_id,device_id=device.device_id,active_since=now-timedelta(minutes=2),last_seen=now-timedelta(seconds=30))])
                    await db.flush()
                    from app.services.rule_selection import alert_rule_is_effective
                    assert await alert_rule_is_effective(db,device,new.rule_id)
                    assert not await alert_rule_is_effective(db,device,inherited.rule_id)
                    @asynccontextmanager
                    async def session():
                        yield db
                    with patch.object(worker,"SessionLocal",session):
                        await worker.evaluate_alert_rules()
                    await db.refresh(inherited_alert)
                    assert inherited_alert.resolved_at is not None
                    assert await db.get(AlertRuleState,(inherited.rule_id,device.device_id)) is None
                    fired=(await db.execute(select(AlertHistory).where(AlertHistory.rule_id==new.rule_id,AlertHistory.resolved_at.is_(None)))).scalar_one()
                    assert fired.threshold_value==50 and fired.metric_value==70
                    await db.close()
                finally:
                    await tx.rollback()
        finally:
            await engine.dispose()
    asyncio.run(run())
