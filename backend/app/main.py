import logging
import os
from contextlib import asynccontextmanager

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from app.api.v1.api import api_router
from app.workers.runner import (
    check_command_timeouts,
    check_high_ram_devices,
    check_offline_devices,
)

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = None
    if os.getenv("TESTING") != "1":
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
        "release_notes": "• Sửa lỗi hiển thị sai tên tài khoản khi đăng xuất / đăng nhập lại\n• Sửa lỗi danh sách PC không ổn định khi chuyển tài khoản\n• Sửa lỗi token tạo máy lúc hiện lúc không sau khi tạo xong",
        "force_update": False
    }
