from datetime import UTC, datetime, timedelta
import json
from pathlib import Path
import os
import shutil


def storage_level(percent):
    if percent >= 90:
        return "critical"
    if percent >= 80:
        return "warning"
    return "normal"


def disk_snapshot():
    mount = "/run/dms-db-volume"
    if not os.path.ismount(mount):
        return {"disk_level": "unknown", "disk_percent": None, "free_bytes": None}
    total, _, free = shutil.disk_usage(mount)
    percent = (total - free) * 100 / total
    return {"disk_level": storage_level(percent), "disk_percent": round(percent, 2), "free_bytes": free}


def backup_snapshot():
    try:
        data = json.loads(Path("/run/dms-backup/backup.json").read_text())
        checked = datetime.fromisoformat(data["checked_at"])
        if datetime.now(UTC) - checked > timedelta(minutes=20):
            data["status"] = "stale"
        return data
    except (OSError, ValueError, TypeError, KeyError):
        return {"status": "unknown"}
