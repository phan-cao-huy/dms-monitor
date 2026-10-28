from sqlalchemy.dialects.postgresql import insert

from app.models.alert import AlertRule

DEFAULT_RULES = [
    ("CPU", "CPU > 90%", 90, 5, "WARNING"),
    ("RAM", "RAM > 90%", 90, 5, "WARNING"),
    ("DISK", "Disk > 90%", 90, 1, "WARNING"),
    ("OFFLINE", "Offline (device setting)", 5, 1, "CRITICAL"),
    ("CPU_TEMP", "CPU temperature > 85 C", 85, 1, "WARNING"),
]


async def seed_default_rules(db, org_id):
    for metric, name, threshold, duration, severity in DEFAULT_RULES:
        await db.execute(insert(AlertRule).values(
            workspace_id=org_id, default_key=metric, metric_type=metric, rule_name=name,
            condition=">", threshold=threshold, duration_minutes=duration,
            severity=severity, cooldown_minutes=30, is_active=True,
        ).on_conflict_do_nothing(index_elements=["workspace_id", "default_key"]))
