import asyncio
from sqlalchemy import text
from app.core.config import settings
from app.db.session import SessionLocal
from app.services.storage_health import disk_snapshot
from app.workers.notifications import firebase_app


async def notify_disk_pressure():
    snapshot = disk_snapshot()
    level = snapshot["disk_level"]
    if level == "unknown":
        return
    async with SessionLocal() as db:
        if level == "normal":
            await db.execute(text("DELETE FROM storage_push_state"))
            await db.commit()
            return
        if not settings.FCM_ENABLED or not settings.FCM_CREDENTIALS_FILE:
            return
        from firebase_admin import messaging
        rows = await db.execute(text("""
            SELECT DISTINCT t.user_id,t.fcm_token FROM auth_tokens t
            JOIN org_members m USING(user_id) JOIN organizations o USING(org_id)
            LEFT JOIN storage_push_state s ON s.user_id=t.user_id
            WHERE o.deleted_at IS NULL AND m.role IN ('Owner','Admin')
              AND t.token_type='REFRESH' AND t.used_at IS NULL AND t.expires_at>now()
              AND t.fcm_token IS NOT NULL AND (s.level IS DISTINCT FROM :level OR s.sent_at<now()-interval '24 hours')
        """), {"level":level})
        recipients = {}
        for uid, token in rows:
            recipients.setdefault(uid, []).append(token)
        percent = snapshot["disk_percent"]
        for uid, tokens in recipients.items():
            data = dict(user_id=str(uid), alert_id="-2147483600", metric_type="SERVER_STORAGE",
                        title_vi="Ổ chứa database sắp đầy", title_en="Database disk running low",
                        body_vi=f"Đã dùng {percent}%. Mở Dung lượng & lưu trữ để kiểm tra.",
                        body_en=f"{percent}% used. Open Storage to review.")
            delivered = False
            for i in range(0,len(tokens),500):
                result = await asyncio.to_thread(messaging.send_each_for_multicast,
                    messaging.MulticastMessage(tokens=tokens[i:i+500],data=data,
                        android=messaging.AndroidConfig(priority="high",ttl=300)),app=firebase_app())
                delivered |= result.success_count > 0
            if delivered:
                await db.execute(text("""
                    INSERT INTO storage_push_state(user_id,level) VALUES (:uid,:level)
                    ON CONFLICT(user_id) DO UPDATE SET level=excluded.level,sent_at=now()
                """), {"uid":uid,"level":level})
                await db.commit()
