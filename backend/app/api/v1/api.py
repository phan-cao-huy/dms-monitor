from fastapi import APIRouter

from app.api.v1.routers import (
    agent,
    alerts,
    auth,
    devices,
    groups,
    health,
    organizations,
)

api_router = APIRouter(prefix="/api/v1")
for r in (health, auth, organizations, groups, devices, alerts, agent):
    api_router.include_router(r.router)
