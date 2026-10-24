import logging
import os
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from sqlalchemy import text

from app.api.v1.api import api_router
from app.db.session import engine
from app.workers.runner import (
    check_command_timeouts,
    check_high_ram_devices,
    check_offline_devices,
)

logger = logging.getLogger(__name__)


async def _ensure_db_schema() -> None:
    try:
        async with engine.begin() as conn:
            await conn.execute(
                text(
                    "ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_monitoring BOOLEAN NOT NULL DEFAULT TRUE;"
                )
            )
            await conn.execute(
                text(
                    "ALTER TABLE devices DROP CONSTRAINT IF EXISTS chk_device_status;"
                )
            )
            await conn.execute(
                text(
                    "ALTER TABLE devices ADD CONSTRAINT chk_device_status CHECK (status IN ('Online', 'Offline', 'Warning', 'Archived', 'Paused'));"
                )
            )
    except Exception:
        logger.warning("Could not auto-migrate schema", exc_info=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = None
    if os.getenv("TESTING") != "1":
        await _ensure_db_schema()
        try:
            scheduler = AsyncIOScheduler()
            scheduler.add_job(check_offline_devices, "interval", minutes=1)
            scheduler.add_job(check_high_ram_devices, "interval", minutes=1)
            scheduler.add_job(check_command_timeouts, "interval", minutes=1)
            scheduler.start()
        except Exception:
            logger.warning("Could not start scheduler", exc_info=True)
    try:
        yield
    finally:
        if scheduler is not None and scheduler.running:
            scheduler.shutdown()


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
        "version_code": 7,
        "version_name": "1.0.6",
        "apk_url": "/static/dms-app.apk",
        "apk_size_bytes": apk_size,
        "release_notes": "• Thêm tính năng bật/tắt theo dõi máy tính từ xa\n• Tự động gỡ cài đặt và xóa toàn bộ file khi xóa PC trên App",
        "force_update": False
    }
