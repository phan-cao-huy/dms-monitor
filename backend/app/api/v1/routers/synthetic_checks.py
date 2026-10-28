import asyncio
from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.models.monitoring import CheckAudit, CheckIncident, CheckResult, SyntheticCheck
from app.models.organization import Organization
from app.schemas.monitoring import CheckWrite, target_parts
from app.services.alerts import AlertError, _get_org_member
from app.services.synthetic_checks import run_check
from app.services.synthetic_probe import TargetBlocked, resolve_target

router = APIRouter(prefix="/orgs/{org_id}/checks", tags=["synthetic-checks"])
DB = Annotated[AsyncSession, Depends(get_db)]
CurrentUser = Annotated[User, Depends(get_current_user)]


async def allowed(db, org_id, user_id):
    try:
        member = await _get_org_member(db, org_id, user_id)
    except AlertError as exc:
        raise HTTPException(exc.status_code, exc.detail) from exc
    if member.role not in ("Owner", "Admin"):
        raise HTTPException(403, "Only Owner/Admin can access workspace checks")


async def check_target(payload):
    host, port, _ = target_parts(payload.type, payload.target)
    try:
        async with asyncio.timeout(5):
            await resolve_target(host, port)
    except (TargetBlocked, ValueError):
        raise HTTPException(422, "Target is blocked by network policy") from None
    except (OSError, TimeoutError):
        # A broken DNS record is itself a useful monitoring target. Every probe revalidates it.
        return


async def get_check(db, org_id, check_id, lock=False):
    stmt = select(SyntheticCheck).where(SyntheticCheck.org_id == org_id, SyntheticCheck.check_id == check_id)
    if lock:
        stmt = stmt.with_for_update()
    row = (await db.execute(stmt)).scalar_one_or_none()
    if row is None:
        raise HTTPException(404, "Check not found")
    return row


def check_out(row):
    return {key: getattr(row, key) for key in ("check_id", "name", "type", "target", "interval_seconds", "timeout_seconds", "failure_threshold", "enabled", "consecutive_failures", "incident_open", "last_checked_at")}


@router.get("")
async def list_checks(org_id: int, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    rows = (await db.execute(select(SyntheticCheck).where(SyntheticCheck.org_id == org_id).order_by(SyntheticCheck.check_id))).scalars().all()
    output = []
    for row in rows:
        latest = (await db.execute(select(CheckResult).where(CheckResult.check_id == row.check_id).order_by(CheckResult.result_id.desc()).limit(1))).scalar_one_or_none()
        output.append(check_out(row) | {"latest": {"ok":latest.ok, "latency_ms":latest.latency_ms, "detail":latest.detail} if latest else None})
    return output


@router.post("", status_code=201)
async def create_check(org_id: int, payload: CheckWrite, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    await db.execute(select(Organization.org_id).where(Organization.org_id == org_id).with_for_update())
    if (await db.scalar(select(func.count()).select_from(SyntheticCheck).where(SyntheticCheck.org_id == org_id))) >= 20:
        raise HTTPException(422, "Maximum 20 checks per workspace")
    if payload.enabled:
        await check_target(payload)
    row = SyntheticCheck(org_id=org_id, **payload.model_dump())
    db.add(row)
    await db.flush()
    db.add(CheckAudit(org_id=org_id, user_id=user.user_id, check_id=row.check_id, action="CREATE"))
    await db.commit()
    await db.refresh(row)
    return check_out(row)


@router.put("/{check_id}")
async def update_check(org_id: int, check_id: int, payload: CheckWrite, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    row = await get_check(db, org_id, check_id, lock=True)
    if payload.enabled:
        await check_target(payload)
    # An edited target starts a fresh failure window and closes the old incident.
    incidents = (await db.execute(select(CheckIncident).where(CheckIncident.check_id == check_id, CheckIncident.resolved_at.is_(None)))).scalars().all()
    for incident in incidents:
        incident.resolved_at = datetime.now(UTC).replace(tzinfo=None)
    for key, value in payload.model_dump().items():
        setattr(row, key, value)
    row.consecutive_failures, row.incident_open, row.last_checked_at = 0, False, None
    db.add(CheckAudit(org_id=org_id, user_id=user.user_id, check_id=check_id, action="UPDATE"))
    await db.commit()
    return check_out(row)


@router.delete("/{check_id}", status_code=204)
async def delete_check(org_id: int, check_id: int, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    row = await get_check(db, org_id, check_id, lock=True)
    db.add(CheckAudit(org_id=org_id, user_id=user.user_id, check_id=check_id, action="DELETE"))
    await db.delete(row)
    await db.commit()
    return Response(status_code=204)


@router.post("/{check_id}/run")
async def run_now(org_id: int, check_id: int, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    row = await get_check(db, org_id, check_id, lock=True)
    if row.last_checked_at and datetime.now(UTC).replace(tzinfo=None) - row.last_checked_at < timedelta(seconds=30):
        raise HTTPException(429, "Wait 30 seconds between checks")
    db.add(CheckAudit(org_id=org_id, user_id=user.user_id, check_id=check_id, action="RUN"))
    return await run_check(db, row)


@router.get("/{check_id}/history")
async def history(org_id: int, check_id: int, db: DB, user: CurrentUser):
    await allowed(db, org_id, user.user_id)
    await get_check(db, org_id, check_id)
    results = (await db.execute(select(CheckResult).where(CheckResult.check_id == check_id).order_by(CheckResult.result_id.desc()).limit(100))).scalars().all()
    incidents = (await db.execute(select(CheckIncident).where(CheckIncident.check_id == check_id).order_by(CheckIncident.incident_id.desc()).limit(50))).scalars().all()
    return {"results":[{"ok":r.ok,"latency_ms":r.latency_ms,"detail":r.detail,"checked_at":r.checked_at} for r in results],
            "incidents":[{"detail":i.detail,"opened_at":i.opened_at,"resolved_at":i.resolved_at} for i in incidents]}
