from datetime import UTC, timedelta

from sqlalchemy import func, or_, select

from app.models.device import Device
from app.models.metrics import DeviceLabel
from app.models.organization import DeviceGroup, UserGroupAccess
from app.services.device_labels import LABEL_KEY, parse_label
from app.services.devices import DeviceError, _get_org_member
from app.services.metric_history import (
    METRICS,
    aggregate_value,
    resolution_for,
    source_for,
    stat_parts,
    step_for,
    window,
)


async def query_metrics(db, org_id, user_id, metric, agg, group_by, start, end, step=None, label=None):
    if metric not in METRICS or agg not in ("avg","min","max"):
        raise DeviceError(422,"Invalid metric or aggregation")
    label_key = group_by[6:] if group_by.startswith("label:") else None
    if group_by not in ("device","group") and (label_key is None or not LABEL_KEY.fullmatch(label_key)):
        raise DeviceError(422,"group_by must be device, group or label:<key>")
    start,end = window(start,end)
    resolution = resolution_for(start,end)
    step = step_for(start,end,resolution,step)
    member = await _get_org_member(db,org_id,user_id)
    devices = select(Device.device_id).where(Device.org_id==org_id,Device.archived_at.is_(None))
    if member.role not in ("Owner","Admin"):
        groups = select(UserGroupAccess.group_id).where(UserGroupAccess.user_id==user_id)
        devices = devices.where(or_(Device.group_id.is_(None),Device.group_id.in_(groups)))
    filter_label = parse_label(label)
    if filter_label:
        devices = devices.where(select(DeviceLabel.device_id).where(DeviceLabel.device_id==Device.device_id,
            DeviceLabel.key==filter_label[0],DeviceLabel.value==filter_label[1]).exists())
    source = source_for(devices,start,end,resolution,metrics=(metric,))
    if group_by=="device":
        key,name = Device.device_id,Device.device_name
    elif group_by=="group":
        key,name = Device.group_id,func.coalesce(DeviceGroup.group_name,"Chưa phân nhóm")
    else:
        key,name = DeviceLabel.value,func.coalesce(DeviceLabel.value,"Chưa gắn nhãn")
    bucket = func.date_bin(timedelta(seconds=step),source.c.stamp,start).label("bucket")
    stmt = select(key.label("key"),name.label("name"),bucket,aggregate_value(source,metric,agg).label("value"),
                  func.sum(stat_parts(source,metric)["count"]).label("sample_count")).select_from(source).join(Device,Device.device_id==source.c.device_id)
    if group_by=="group":
        stmt = stmt.outerjoin(DeviceGroup,DeviceGroup.group_id==Device.group_id)
    elif label_key is not None:
        stmt = stmt.outerjoin(DeviceLabel,(DeviceLabel.device_id==Device.device_id)&(DeviceLabel.key==label_key))
    rows = (await db.execute(stmt.group_by(key,name,bucket).order_by(key,bucket).limit(50001))).all()
    series = {}
    for row in rows:
        identifier = (row.key is None, str(row.key))
        series.setdefault(identifier,{"key":row.key,"name":row.name,"points":[]})["points"].append({
            "timestamp":row.bucket.replace(tzinfo=UTC),"value":row.value,"sample_count":int(row.sample_count or 0)})
        if len(series)>100:
            raise DeviceError(422,"Maximum 100 series; narrow the selection using a label filter")
    return {"metric":metric,"agg":agg,"group_by":group_by,"resolution":resolution,"step_seconds":step,
            "from":start.replace(tzinfo=UTC),"to":end.replace(tzinfo=UTC),"series":list(series.values())}
