"""One effective rule per device and component; explicit scope beats inheritance."""


def component_key(rule):
    subject = (rule.service_name or "") if rule.metric_type == "SERVICE" else (
        (rule.process_name or "") if rule.metric_type == "PROCESS_MISSING" else ""
    )
    return rule.metric_type, subject


def effective_rules(device, rules):
    selected = {}
    for rule in rules:
        if not rule.is_active:
            continue
        if rule.device_id is not None:
            priority = 3 if rule.device_id == device.device_id else 0
        elif rule.group_id is not None:
            priority = 2 if rule.group_id == device.group_id else 0
        else:
            priority = 1 if rule.workspace_id == device.org_id else 0
        if not priority:
            continue
        key = component_key(rule)
        rank = (priority, rule.rule_id)
        if key not in selected or rank > selected[key][0]:
            selected[key] = rank, rule
    return [item[1] for item in selected.values()]


async def alert_rule_is_effective(db, device, rule_id):
    from sqlalchemy import or_, select
    from app.models.alert import AlertRule
    rows = (await db.execute(select(AlertRule).where(or_(
        AlertRule.device_id == device.device_id,
        AlertRule.group_id == device.group_id if device.group_id is not None else False,
        AlertRule.workspace_id == device.org_id,
    )))).scalars().all()
    return any(rule.rule_id == rule_id for rule in effective_rules(device, rows))
