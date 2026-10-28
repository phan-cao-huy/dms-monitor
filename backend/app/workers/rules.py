"""Persistent rule evaluation; missing/stale observations never count as failures."""

import operator
from datetime import UTC, datetime, timedelta

from sqlalchemy import or_, select

from app.db.session import SessionLocal
from app.models.alert import AlertHistory, AlertRule, AlertRuleState, MaintenanceWindow
from app.models.device import Device, DeviceMetric
from app.services.alert_text import alert_text

COMPARATORS = {">": operator.gt, ">=": operator.ge, "<": operator.lt, "<=": operator.le}
METRIC_FIELDS = {
    "CPU": "cpu_usage",
    "RAM": "ram_usage",
    "DISK": "disk_usage",
    "NET_IN": "net_in_kbps",
    "NET_OUT": "net_out_kbps",
}


def rule_observation(rule, device, metric, now):
    if device.archived_at is not None or device.is_monitoring is False:
        return None, None
    if rule.metric_type == "OFFLINE":
        if device.last_heartbeat is None:
            return None, None
        value = (now - device.last_heartbeat).total_seconds() / 60
    elif device.last_heartbeat is None or now - device.last_heartbeat > timedelta(
        seconds=120
    ):
        return None, None
    elif rule.metric_type == "GPU":
        value = (device.static_info or {}).get("gpu_usage_pct")
    elif rule.metric_type in METRIC_FIELDS:
        if metric is None or now - metric.created_at > timedelta(seconds=120):
            return None, None
        value = getattr(metric, METRIC_FIELDS[rule.metric_type])
    else:
        category = "services" if rule.metric_type == "SERVICE" else "processes"
        name = rule.service_name if category == "services" else rule.process_name
        observed = (device.watch_status or {}).get(category, {}).get(name)
        if not isinstance(observed, bool):
            return None, None
        return not observed, float(not observed)
    if value is None or rule.threshold is None:
        return None, None
    return COMPARATORS[rule.condition](value, rule.threshold), float(value)


def update_observation(state, breached, now):
    if breached is not True:
        state.active_since = None
    elif (
        state.active_since is None
        or state.last_seen is None
        or now - state.last_seen > timedelta(seconds=120)
    ):
        state.active_since = now
    state.last_seen = now


async def evaluate_device_rules(db, device, rules, now):
    metric = (
        await db.execute(
            select(DeviceMetric)
            .where(
                DeviceMetric.device_id == device.device_id,
                DeviceMetric.created_at <= now,
            )
            .order_by(DeviceMetric.created_at.desc())
            .limit(1)
        )
    ).scalar_one_or_none()
    maintenance = (
        await db.execute(
            select(MaintenanceWindow.window_id)
            .where(
                or_(
                    MaintenanceWindow.device_id == device.device_id,
                    MaintenanceWindow.group_id == device.group_id
                    if device.group_id is not None
                    else False,
                ),
                MaintenanceWindow.start_time <= now,
                MaintenanceWindow.end_time > now,
            )
            .limit(1)
        )
    ).scalar_one_or_none()
    for rule in rules:
        state = await db.get(AlertRuleState, (rule.rule_id, device.device_id))
        if state is None:
            state = AlertRuleState(rule_id=rule.rule_id, device_id=device.device_id)
            db.add(state)
        breached, value = rule_observation(rule, device, metric, now)
        if maintenance is not None:
            breached = None
        update_observation(state, breached, now)
        latest = (
            await db.execute(
                select(AlertHistory)
                .where(
                    AlertHistory.rule_id == rule.rule_id,
                    AlertHistory.device_id == device.device_id,
                )
                .order_by(AlertHistory.created_at.desc(), AlertHistory.alert_id.desc())
                .limit(1)
            )
        ).scalar_one_or_none()
        if breached is False:
            if latest is not None and latest.resolved_at is None:
                latest.resolved_at = now
            continue
        if breached is not True or now - state.active_since < timedelta(
            minutes=rule.duration_minutes
        ):
            continue
        copy = alert_text(rule.metric_type,device.device_name,value,rule.threshold,rule.condition,
                          rule.duration_minutes,rule.service_name,rule.process_name,device.top_processes)
        if latest is not None:
            if latest.resolved_at is None:
                latest.metric_value = value
                latest.threshold_value = rule.threshold
                latest.message = f"{copy['title_vi']}\n{copy['body_vi']}"
                latest.message_en = f"{copy['title_en']}\n{copy['body_en']}"
                continue
            if now - latest.created_at < timedelta(minutes=rule.cooldown_minutes):
                continue
        db.add(
            AlertHistory(
                device_id=device.device_id,
                rule_id=rule.rule_id,
                severity=rule.severity,
                message=f"{copy['title_vi']}\n{copy['body_vi']}",
                message_en=f"{copy['title_en']}\n{copy['body_en']}",
                metric_value=value,
                threshold_value=rule.threshold,
                notification_status="PENDING",
            )
        )
        if device.status in ("Online", "Warning"):
            device.status = "Warning"


async def evaluate_alert_rules():
    now = datetime.now(UTC).replace(tzinfo=None)
    async with SessionLocal() as db:
        rules = list(
            (await db.execute(select(AlertRule).where(AlertRule.is_active.is_(True))))
            .scalars()
            .all()
        )
        devices = list(
            (await db.execute(select(Device).where(Device.archived_at.is_(None))))
            .scalars()
            .all()
        )
        for device in devices:
            matching = [
                r
                for r in rules
                if r.device_id == device.device_id
                or (r.group_id is not None and r.group_id == device.group_id)
            ]
            if matching:
                await evaluate_device_rules(db, device, matching, now)
        await db.commit()
