import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from app.api.v1.api import api_router

app = FastAPI(
    title="DMS - Device Monitoring System",
    description="Backend API Server cho hệ thống giám sát máy tính từ xa (Mobile App & Agent).",
    version="0.1.0",
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
        media_type = "application/vnd.android.package-archive" if filename.endswith(".apk") else None
        return FileResponse(file_path, filename=filename, media_type=media_type)
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

