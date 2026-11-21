"""Expire only resolved device alerts for workspaces that opted in."""
from sqlalchemy import text
from app.db.session import SessionLocal


async def prune_resolved_alerts():
    async with SessionLocal() as db:
        orgs = list(await db.scalars(text("SELECT org_id FROM workspace_alert_retention")))
    for org_id in orgs:
        while True:
            async with SessionLocal() as db:
                days = await db.scalar(text("""
                    SELECT days FROM workspace_alert_retention WHERE org_id=:org_id FOR SHARE
                """), {"org_id": org_id})
                if days is None:
                    break
                result = await db.execute(text("""
                    WITH candidates AS (
                        SELECT a.alert_id FROM alert_history a JOIN devices d USING(device_id)
                        WHERE d.org_id=:org_id AND a.resolved_at <
                            (now() AT TIME ZONE 'UTC') - make_interval(days => :days)
                        ORDER BY a.alert_id LIMIT 2000 FOR UPDATE OF a
                    )
                    DELETE FROM alert_history a USING candidates c WHERE a.alert_id=c.alert_id
                """), {"days": days, "org_id": org_id})
                await db.commit()
                if result.rowcount < 2000:
                    break
