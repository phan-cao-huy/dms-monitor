from datetime import UTC, datetime
from typing import NoReturn

from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.alert import AlertHistory, AlertRule
from app.models.device import Device
from app.models.organization import (
    DeviceGroup,
    Organization,
    OrganizationMember,
    UserGroupAccess,
)
from app.schemas.alert import AlertRuleCreate
from app.services.devices import DeviceError, check_user_device_access


class AlertError(Exception):
    def __init__(self, status_code: int, detail: str) -> None:
        self.status_code = status_code
        self.detail = detail
        super().__init__(detail)


def _fail(status_code: int, detail: str) -> NoReturn:
    raise AlertError(status_code, detail)


def _utc_naive() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


async def _get_org_member(
    db: AsyncSession, org_id: int, user_id: int
) -> OrganizationMember:
    result = await db.execute(
        select(OrganizationMember)
        .join(Organization, Organization.org_id == OrganizationMember.org_id)
        .where(
            OrganizationMember.org_id == org_id,
            OrganizationMember.user_id == user_id,
            Organization.deleted_at.is_(None),
        )
    )
    member = result.scalar_one_or_none()
    if member is None:
        _fail(403, "You are not a member of this organization")
    return member


async def list_alert_rules(
    db: AsyncSession, org_id: int, user_id: int
) -> list[AlertRule]:
    member = await _get_org_member(db, org_id, user_id)
    device_query = select(Device.device_id).where(Device.org_id == org_id)
    group_query = select(DeviceGroup.group_id).where(DeviceGroup.org_id == org_id)
    if member.role not in ("Owner", "Admin"):
        assigned_groups = select(UserGroupAccess.group_id).where(UserGroupAccess.user_id == user_id)
        device_query = device_query.where(or_(Device.group_id.is_(None), Device.group_id.in_(assigned_groups)))
        group_query = group_query.where(DeviceGroup.group_id.in_(assigned_groups))
    dev_res = await db.execute(device_query)
    device_ids = set(dev_res.scalars().all())
    grp_res = await db.execute(group_query)
    group_ids = set(grp_res.scalars().all())

    stmt = select(AlertRule).where(
        (AlertRule.device_id.in_(device_ids)) | (AlertRule.group_id.in_(group_ids))
    ).order_by(AlertRule.rule_id.desc())

    res = await db.execute(stmt)
    return list(res.scalars().all())


async def create_alert_rule(
    db: AsyncSession, org_id: int, user_id: int, payload: AlertRuleCreate
) -> AlertRule:
    member = await _get_org_member(db, org_id, user_id)
    if member.role not in ("Owner", "Admin"):
        _fail(403, "Only Owner or Admin can create alert rules")

    if payload.device_id is None and payload.group_id is None:
        _fail(422, "Either device_id or group_id must be specified")

    if payload.device_id is not None:
        dev_res = await db.execute(
            select(Device).where(
                Device.device_id == payload.device_id, Device.org_id == org_id
            )
        )
        if dev_res.scalar_one_or_none() is None:
            _fail(404, "Device not found in this organization")

    if payload.group_id is not None:
        grp_res = await db.execute(
            select(DeviceGroup).where(
                DeviceGroup.group_id == payload.group_id,
                DeviceGroup.org_id == org_id,
            )
        )
        if grp_res.scalar_one_or_none() is None:
            _fail(404, "Device group not found in this organization")

    rule = AlertRule(
        group_id=payload.group_id,
        device_id=payload.device_id,
        rule_name=payload.rule_name,
        metric_type=payload.metric_type,
        condition=payload.condition,
        threshold=payload.threshold,
        service_name=payload.service_name,
        process_name=payload.process_name,
        duration_minutes=payload.duration_minutes,
        severity=payload.severity,
        cooldown_minutes=payload.cooldown_minutes,
        created_by=user_id,
    )
    db.add(rule)
    await db.commit()
    await db.refresh(rule)
    return rule


async def delete_alert_rule(
    db: AsyncSession, rule_id: int, user_id: int
) -> None:
    res = await db.execute(select(AlertRule).where(AlertRule.rule_id == rule_id))
    rule = res.scalar_one_or_none()
    if rule is None:
        _fail(404, "Alert rule not found")

    # Find the org of the rule
    org_id = None
    if rule.device_id:
        dev_res = await db.execute(
            select(Device.org_id).where(Device.device_id == rule.device_id)
        )
        org_id = dev_res.scalar_one_or_none()
    elif rule.group_id:
        grp_res = await db.execute(
            select(DeviceGroup.org_id).where(DeviceGroup.group_id == rule.group_id)
        )
        org_id = grp_res.scalar_one_or_none()

    if org_id is not None:
        member = await _get_org_member(db, org_id, user_id)
        if member.role not in ("Owner", "Admin"):
            _fail(403, "Only Owner or Admin can delete alert rules")

    await db.delete(rule)
    await db.commit()


async def list_org_alerts(
    db: AsyncSession,
    org_id: int,
    user_id: int,
    resolved: bool | None = None,
    limit: int = 100,
) -> list[AlertHistory]:
    member = await _get_org_member(db, org_id, user_id)
    devices_query = select(Device.device_id).where(Device.org_id == org_id)
    if member.role not in ("Owner", "Admin"):
        devices_query = devices_query.where(or_(
            Device.group_id.is_(None),
            Device.group_id.in_(select(UserGroupAccess.group_id).where(UserGroupAccess.user_id == user_id)),
        ))
    dev_res = await db.execute(devices_query)
    device_ids = set(dev_res.scalars().all())

    stmt = select(AlertHistory).where(AlertHistory.device_id.in_(device_ids))
    if resolved is False:
        stmt = stmt.where(AlertHistory.resolved_at.is_(None))
    elif resolved is True:
        stmt = stmt.where(AlertHistory.resolved_at.is_not(None))

    stmt = stmt.order_by(AlertHistory.created_at.desc()).limit(limit)
    res = await db.execute(stmt)
    return list(res.scalars().all())


async def resolve_alert(
    db: AsyncSession, alert_id: int, user_id: int
) -> AlertHistory:
    res = await db.execute(
        select(AlertHistory).where(AlertHistory.alert_id == alert_id)
    )
    alert = res.scalar_one_or_none()
    if alert is None:
        _fail(404, "Alert not found")

    try:
        await check_user_device_access(db, alert.device_id, user_id)
    except DeviceError as exc:
        _fail(exc.status_code, exc.detail)

    alert.resolved_at = _utc_naive()
    await db.commit()
    await db.refresh(alert)
    return alert


async def acknowledge_alert(
    db: AsyncSession, alert_id: int, user_id: int
) -> AlertHistory:
    res = await db.execute(
        select(AlertHistory).where(AlertHistory.alert_id == alert_id)
    )
    alert = res.scalar_one_or_none()
    if alert is None:
        _fail(404, "Alert not found")

    try:
        await check_user_device_access(db, alert.device_id, user_id)
    except DeviceError as exc:
        _fail(exc.status_code, exc.detail)

    alert.acknowledged_by = user_id
    alert.acknowledged_at = _utc_naive()
    await db.commit()
    await db.refresh(alert)
    return alert
