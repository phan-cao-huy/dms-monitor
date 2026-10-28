import asyncio
from unittest.mock import AsyncMock, Mock

import pytest
from app.models.alert import AlertRule
from app.models.device import Device
from app.models.organization import OrganizationMember
from app.schemas.alert import AlertRuleCreate, AlertRuleUpdate
from app.services.alerts import AlertError, update_alert_rule
from pydantic import ValidationError


@pytest.mark.parametrize("metric,value", [("CPU", 101), ("GPU", 101), ("RAM", -1), ("DISK", 101), ("OFFLINE", 0)])
def test_invalid_thresholds_rejected(metric, value):
    with pytest.raises(ValidationError):
        AlertRuleCreate(device_id=1, metric_type=metric, threshold=value)


def session(role="Admin", target_org=10):
    member = OrganizationMember(org_id=10, user_id=1, role=role)
    rule = AlertRule(rule_id=2, device_id=3, group_id=None, rule_name="CPU", metric_type="CPU",
                     condition=">=", threshold=90, duration_minutes=2, cooldown_minutes=30,
                     severity="WARNING", is_active=True, service_name=None, process_name=None)
    result = Mock()
    result.scalar_one_or_none.return_value = member
    db = Mock(execute=AsyncMock(return_value=result),
              get=AsyncMock(side_effect=[rule, Device(device_id=3, org_id=target_org)]),
              commit=AsyncMock(), refresh=AsyncMock())
    return db, rule


def test_threshold_update_preserves_identity_and_resets_window():
    db, _rule = session()
    result = asyncio.run(update_alert_rule(db, 10, 2, 1, AlertRuleUpdate(threshold=75)))
    assert result.rule_id == 2 and result.threshold == 75
    assert result.duration_minutes == 2
    assert "DELETE FROM alert_rule_states" in str(db.execute.call_args_list[-1].args[0])
    db.commit.assert_awaited_once()


@pytest.mark.parametrize("role,target_org,code", [("Viewer", 10, 403), ("Admin", 11, 404)])
def test_threshold_update_requires_admin_and_same_org(role, target_org, code):
    db, _ = session(role, target_org)
    with pytest.raises(AlertError) as error:
        asyncio.run(update_alert_rule(db, 10, 2, 1, AlertRuleUpdate(threshold=75)))
    assert error.value.status_code == code
    db.commit.assert_not_awaited()


def test_invalid_update_does_not_mutate_rule():
    db, rule = session()
    with pytest.raises(AlertError) as error:
        asyncio.run(update_alert_rule(db, 10, 2, 1, AlertRuleUpdate(threshold=101)))
    assert error.value.status_code == 422
    assert rule.threshold == 90
    db.commit.assert_not_awaited()
