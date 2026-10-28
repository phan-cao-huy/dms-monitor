import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text

from app.api.v1.api import api_router
from app.core.config import settings
from app.db.session import engine
from app.workers.runner import worker_lifespan

logger = logging.getLogger(__name__)


async def _ensure_db_schema() -> None:
    migration_statements = [
        "ALTER TABLE alert_history ADD COLUMN IF NOT EXISTS message_en TEXT;",
        "ALTER TABLE alert_rules DROP CONSTRAINT IF EXISTS chk_rule_metric;",
        "ALTER TABLE alert_rules ADD CONSTRAINT chk_rule_metric CHECK (metric_type IN ('CPU','RAM','GPU','DISK','NET_IN','NET_OUT','OFFLINE','SERVICE','PROCESS_MISSING'));",
        "ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_monitoring BOOLEAN NOT NULL DEFAULT TRUE;",
        "ALTER TABLE devices DROP CONSTRAINT IF EXISTS chk_device_status;",
        "ALTER TABLE devices DROP CONSTRAINT IF EXISTS devices_status_check;",
        "ALTER TABLE devices ADD CONSTRAINT chk_device_status CHECK (status IN ('Online', 'Offline', 'Warning', 'Archived', 'Paused'));",
        "ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS chk_cmd_type;",
        "ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS device_commands_command_type_check;",
        "ALTER TABLE device_commands ADD CONSTRAINT chk_cmd_type CHECK (command_type IN ('REBOOT', 'RESTART_SERVICE', 'KILL_PROCESS', 'PAUSE_MONITORING', 'RESUME_MONITORING', 'UNINSTALL'));",
    ]
    async with engine.begin() as conn:
        for stmt in migration_statements:
            await conn.execute(text(stmt))



@asynccontextmanager
async def lifespan(app: FastAPI):
    if os.getenv("TESTING") != "1":
        await _ensure_db_schema()
    if settings.EMBEDDED_WORKER and os.getenv("TESTING") != "1":
        async with worker_lifespan():
            yield
    else:
        yield


app = FastAPI(
    title="DMS - Device Monitoring System",
    description="Backend API Server cho hệ thống giám sát máy tính từ xa (Mobile App & Agent).",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)

STATIC_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static")

@app.api_route("/static/{filename}", methods=["GET", "HEAD"])
def get_static_file(filename: str):
    file_path = os.path.join(STATIC_DIR, filename)
    if os.path.isfile(file_path):
        if filename.endswith(".apk"):
            media_type = "application/vnd.android.package-archive"
        elif filename.endswith(".ps1"):
            media_type = "text/plain; charset=utf-8"
        elif filename.endswith(".exe"):
            media_type = "application/octet-stream"
        else:
            media_type = None
        headers = {
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
        }
        return FileResponse(file_path, filename=filename, media_type=media_type, headers=headers)
    raise HTTPException(status_code=404, detail="File not found")

@app.get("/api/v1/app/version")
def get_app_version():
    apk_path = os.path.join(STATIC_DIR, "dms-app.apk")
    apk_size = os.path.getsize(apk_path) if os.path.isfile(apk_path) else 0
    return {
        "web_version": "1.0.20",
        "version_code": 21,
        "version_name": "1.0.20",
        "apk_url": "/static/dms-app.apk",
        "apk_size_bytes": apk_size,
        "release_notes": "• Khôi phục câu Đang thực thi lệnh...\n• Tách dừng một PID và đóng tất cả tiến trình cùng tên\n• Hiện rõ phạm vi và cảnh báo trước khi đóng toàn ứng dụng",
        "force_update": False
    }


# Web uses hash-based routes, the existing JWT API and the same database.
# Mount after API/docs/download routes so they keep their existing behaviour.
WEB_DIR = os.path.join(STATIC_DIR, "web")
app.mount("/", StaticFiles(directory=WEB_DIR, html=True, check_dir=False), name="dms-web")
