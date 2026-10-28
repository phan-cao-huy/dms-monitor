"""Optional FCM delivery using active refresh sessions and device access scope."""

import asyncio
import logging
from datetime import UTC, datetime, timedelta

from sqlalchemy import or_, select

from app.core.config import settings
from app.db.session import SessionLocal
from app.models.alert import AlertHistory, AlertRule
from app.models.auth import AuthToken
from app.models.device import Device
from app.models.organization import OrganizationMember, UserGroupAccess
from app.services.alert_text import alert_text

logger = logging.getLogger(__name__)


def firebase_app():
    import firebase_admin
    from firebase_admin import credentials

    try:
        return firebase_admin.get_app("dms-alerts")
    except ValueError:
        return firebase_admin.initialize_app(
            credentials.Certificate(settings.FCM_CREDENTIALS_FILE), name="dms-alerts"
        )


async def send_pending_notifications():
    if not settings.FCM_ENABLED or not settings.FCM_CREDENTIALS_FILE:
        return
    from firebase_admin import messaging

    app = firebase_app()
    now = datetime.now(UTC).replace(tzinfo=None)
    async with SessionLocal() as db:
        alerts = (
            (
                await db.execute(
                    select(AlertHistory)
                    .where(
                        AlertHistory.notification_status.in_(("PENDING", "FAILED")),
                        AlertHistory.created_at >= now - timedelta(hours=24),
                        AlertHistory.resolved_at.is_(None),
                    )
                    .order_by(AlertHistory.created_at)
                    .limit(100)
                    .with_for_update(skip_locked=True)
                )
            )
            .scalars()
            .all()
        )
        for alert in alerts:
            device = await db.get(Device, alert.device_id)
            if device is None or device.archived_at is not None:
                continue
            allowed_group = (
                select(UserGroupAccess.access_id)
                .where(
                    UserGroupAccess.user_id == AuthToken.user_id,
                    UserGroupAccess.group_id == device.group_id,
                )
                .exists()
            )
            tokens = (
                (
                    await db.execute(
                        select(AuthToken.fcm_token, AuthToken.user_id)
                        .join(
                            OrganizationMember,
                            OrganizationMember.user_id == AuthToken.user_id,
                        )
                        .where(
                            OrganizationMember.org_id == device.org_id,
                            or_(
                                OrganizationMember.role.in_(("Owner", "Admin")),
                                device.group_id is None,
                                allowed_group,
                            ),
                            AuthToken.token_type == "REFRESH",
                            AuthToken.used_at.is_(None),
                            AuthToken.expires_at > now,
                            AuthToken.fcm_token.is_not(None),
                        )
                    )
                )
                .all()
            )
            recipients = {}
            for token, user_id in tokens:
                recipients.setdefault(user_id, set()).add(token)
            if not tokens:
                continue
            success = True
            try:
                rule = await db.get(AlertRule, alert.rule_id) if alert.rule_id else None
                metric_type = rule.metric_type if rule else ("RAM" if alert.message.startswith("RAM") else "OFFLINE")
                copy = alert_text(metric_type, device.device_name, alert.metric_value, alert.threshold_value,
                                  rule.condition if rule else ">=", rule.duration_minutes if rule else 1,
                                  rule.service_name if rule else None, rule.process_name if rule else None,
                                  processes=device.top_processes if device.last_heartbeat and now-device.last_heartbeat<timedelta(seconds=120) else None)
                # FCM data has a 4 KB budget. Preserve the measurement at the start of each body.
                for key, text in copy.items():
                    limit = 180 if key.startswith("title_") else 850
                    copy[key] = text.encode("utf-8")[:limit].decode("utf-8", errors="ignore")
                for user_id, user_tokens in recipients.items():
                    user_tokens = sorted(user_tokens)
                    for start in range(0, len(user_tokens), 500):
                        batch = user_tokens[start : start + 500]
                        response = await asyncio.to_thread(
                            messaging.send_each_for_multicast,
                            messaging.MulticastMessage(
                                tokens=batch,
                                android=messaging.AndroidConfig(priority="high", ttl=300),
                                data={
                                    "title": copy["title_vi"],
                                    "body": copy["body_vi"],
                                    **copy,
                                    "device_id": str(device.device_id),
                                    "alert_id": str(alert.alert_id),
                                    "user_id": str(user_id),
                                    "metric_type": metric_type,
                                },
                            ),
                            app=app,
                        )
                        success = success and response.failure_count == 0
            except Exception:
                logger.exception("FCM delivery failed for alert %s", alert.alert_id)
                success = False
            alert.notification_status = "SENT" if success else "FAILED"
            if success:
                alert.notified_at = now
        await db.commit()
