"""Optional FCM delivery using active refresh sessions and device access scope."""

import asyncio
import logging
from datetime import UTC, datetime

from sqlalchemy import or_, select

from app.core.config import settings
from app.db.session import SessionLocal
from app.models.alert import AlertHistory
from app.models.auth import AuthToken
from app.models.device import Device
from app.models.organization import OrganizationMember, UserGroupAccess

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
                        AlertHistory.notification_status == "PENDING",
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
                        select(AuthToken.fcm_token)
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
                .scalars()
                .all()
            )
            tokens = sorted(set(tokens))
            if not tokens:
                continue
            success = True
            try:
                for start in range(0, len(tokens), 500):
                    response = await asyncio.to_thread(
                        messaging.send_each_for_multicast,
                        messaging.MulticastMessage(
                            tokens=tokens[start : start + 500],
                            notification=messaging.Notification(
                                title="DMS Monitor", body=alert.message
                            ),
                            data={
                                "device_id": str(device.device_id),
                                "alert_id": str(alert.alert_id),
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
