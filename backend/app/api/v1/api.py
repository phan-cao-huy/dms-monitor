from fastapi import APIRouter

from app.api.v1.routers import (
    agent,
    alerts,
    auth,
    devices,
    email_channels,
    groups,
    health,
    monitoring,
    organizations,
    synthetic_checks,
)

api_router = APIRouter(prefix="/api/v1")
for r in (health, auth, organizations, groups, devices, alerts, agent, email_channels, monitoring, synthetic_checks):
    api_router.include_router(r.router)
