from datetime import datetime
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.auth import User
from app.services.device_labels import labels_for, remove_label, set_label
from app.services.devices import DeviceError, check_user_device_access
from app.services.metrics_query import query_metrics

router = APIRouter(tags=["metric-query-and-labels"])
DB = Annotated[AsyncSession,Depends(get_db)]
CurrentUser = Annotated[User,Depends(get_current_user)]


class LabelWrite(BaseModel):
    value: str = Field(max_length=64, pattern=r"^[^\x00-\x1f\x7f]*$")


async def run(operation):
    try:
        return await operation
    except DeviceError as exc:
        raise HTTPException(exc.status_code,exc.detail) from exc


@router.get("/devices/{device_id}/labels")
async def get_labels(device_id: int, db: DB, user: CurrentUser):
    await run(check_user_device_access(db,device_id,user.user_id))
    return (await labels_for(db,[device_id])).get(device_id,{})


@router.put("/devices/{device_id}/labels/{key}")
async def put_label(device_id: int, key: str, payload: LabelWrite, db: DB, user: CurrentUser):
    return await run(set_label(db,device_id,user.user_id,key,payload.value))


@router.delete("/devices/{device_id}/labels/{key}",status_code=204)
async def delete_label(device_id: int, key: str, db: DB, user: CurrentUser):
    await run(remove_label(db,device_id,user.user_id,key))
    return Response(status_code=204)


@router.get("/orgs/{org_id}/metrics/query")
async def metrics_query(org_id: int, db: DB, user: CurrentUser,
                        metric: Literal["cpu_usage","ram_usage","disk_usage","net_in_kbps","net_out_kbps","gpu_usage_pct","cpu_temp_c"]="cpu_usage",
                        agg: Literal["avg","min","max"]="avg", group_by: str="device",
                        start: Annotated[datetime | None,Query(alias="from")]=None,
                        end: Annotated[datetime | None,Query(alias="to")]=None,
                        step: int | None=Query(default=None,ge=1,le=86400),label: str | None=None):
    return await run(query_metrics(db,org_id,user.user_id,metric,agg,group_by,start,end,step,label))
