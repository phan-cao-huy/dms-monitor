import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock,Mock,patch

import pytest
from app.models.alert import AlertHistory
from app.models.device import Device
from app.workers.notifications import send_pending_notifications
from firebase_admin import messaging


@pytest.mark.parametrize("valid,error,expected",[
    (True,messaging.UnregisteredError("expired"),"SENT"),
    (False,messaging.UnregisteredError("expired"),"FAILED"),
    (True,RuntimeError("temporary"),"FAILED"),
])
def test_expired_tokens_do_not_mask_delivery_or_revoke_sessions(valid,error,expected):
    alert=AlertHistory(alert_id=1,device_id=2,message="test",notification_status="PENDING")
    alerts=Mock();alerts.scalars.return_value.all.return_value=[alert]
    tokens=Mock();tokens.all.return_value=[("a-expired",1)]+([("z-valid",1)] if valid else [])
    db=Mock(execute=AsyncMock(side_effect=[alerts,tokens,Mock()]),
        get=AsyncMock(return_value=Device(device_id=2,org_id=3,group_id=4)),commit=AsyncMock())
    context=AsyncMock();context.__aenter__.return_value=db
    result=SimpleNamespace(success_count=int(valid),responses=[SimpleNamespace(success=False,exception=error)]+
        ([SimpleNamespace(success=True,exception=None)] if valid else []))
    with patch("app.workers.notifications.settings") as config,patch("app.workers.notifications.SessionLocal",return_value=context),patch("app.workers.notifications.firebase_app",return_value=Mock()),patch("firebase_admin.messaging.send_each_for_multicast",return_value=result):
        config.FCM_ENABLED=True;config.FCM_CREDENTIALS_FILE="unused"
        asyncio.run(send_pending_notifications())
    assert alert.notification_status==expected
    if isinstance(error,messaging.UnregisteredError):
        stmt=db.execute.call_args_list[2].args[0]
        assert stmt.compile().params=={'fcm_token':None,'fcm_token_1':['a-expired']}
        assert "used_at" not in str(stmt) and "DELETE" not in str(stmt)
    else:
        assert db.execute.await_count==2


def test_shadowed_rule_is_retired_before_fcm_send_even_before_evaluator_tick():
    alert=AlertHistory(alert_id=1,rule_id=10,device_id=2,message="old default",notification_status="PENDING")
    rows=Mock();rows.scalars.return_value.all.return_value=[alert]
    db=Mock(execute=AsyncMock(return_value=rows),get=AsyncMock(return_value=Device(device_id=2,org_id=3)),commit=AsyncMock())
    context=AsyncMock();context.__aenter__.return_value=db
    with patch("app.workers.notifications.settings") as config,patch("app.workers.notifications.SessionLocal",return_value=context),patch("app.workers.notifications.firebase_app",return_value=Mock()),patch("app.workers.notifications.alert_rule_is_effective",new=AsyncMock(return_value=False)),patch("firebase_admin.messaging.send_each_for_multicast") as sender:
        config.FCM_ENABLED=True;config.FCM_CREDENTIALS_FILE="unused"
        asyncio.run(send_pending_notifications())
    sender.assert_not_called()
    assert alert.resolved_at is not None
    assert alert.notification_status=="PENDING"
