from fastapi import APIRouter

from app.api.v1.routers import (
    agent,
    alerts,
    auth,
    devices,
    groups,
    health,
    metrics,
    monitoring,
    organizations,
    synthetic_checks,
    storage,
)

api_router = APIRouter(prefix="/api/v1")
for r in (health, auth, organizations, groups, devices, alerts, agent, monitoring, synthetic_checks, metrics, storage):
    api_router.include_router(r.router)
