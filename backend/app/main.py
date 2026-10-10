from fastapi import FastAPI

from app.api.v1.api import api_router

app = FastAPI(title="DMS - Device Monitoring System", version="0.1.0")
app.include_router(api_router)
