from datetime import UTC, datetime, timedelta

from sqlalchemy import select

from app.models.monitoring import CheckIncident, CheckResult
from app.services.synthetic_probe import probe


async def run_check(db, check):
    now = datetime.now(UTC).replace(tzinfo=None)
    ok, latency, detail = await probe(check)
    check.last_checked_at = now
    check.consecutive_failures = 0 if ok else check.consecutive_failures + 1
    row = CheckResult(check_id=check.check_id, ok=ok, latency_ms=latency, detail=detail, checked_at=now)
    db.add(row)
    incident = (await db.execute(select(CheckIncident).where(CheckIncident.check_id == check.check_id,
                                  CheckIncident.resolved_at.is_(None)).order_by(CheckIncident.incident_id.desc()).limit(1))).scalar_one_or_none()
    if ok:
        if incident:
            incident.resolved_at = now
        check.incident_open = False
    elif check.consecutive_failures >= check.failure_threshold or detail.startswith("SSL_DAYS:"):
        check.incident_open = True
        if incident:
            incident.detail = detail
        else:
            db.add(CheckIncident(check_id=check.check_id, detail=detail, opened_at=now))
    await db.commit()
    return {"ok": ok, "latency_ms": latency, "detail": detail, "checked_at": now}


def due(check, now):
    return check.last_checked_at is None or now - check.last_checked_at >= timedelta(seconds=check.interval_seconds)
