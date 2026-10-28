import re

from sqlalchemy import delete, func, select
from sqlalchemy.dialects.postgresql import insert

from app.models.device import Device
from app.models.metrics import DeviceLabel
from app.services.devices import DeviceError, check_user_device_access

LABEL_KEY = re.compile(r"^[a-z][a-z0-9_]{0,31}$")


def parse_label(value):
    if value is None:
        return None
    key, sep, label = value.partition(":")
    if not sep or not LABEL_KEY.fullmatch(key) or len(label)>64:
        raise DeviceError(422, "Label filter must be key:value with a valid key and value <=64 characters")
    return key,label


async def labels_for(db, device_ids):
    rows = (await db.execute(select(DeviceLabel).where(DeviceLabel.device_id.in_(device_ids)).order_by(DeviceLabel.key))).scalars().all()
    result = {}
    for row in rows:
        result.setdefault(row.device_id,{})[row.key] = row.value
    return result


async def set_label(db, device_id, user_id, key, value):
    await check_user_device_access(db,device_id,user_id,required_roles=["Owner","Admin","Manager"])
    if not LABEL_KEY.fullmatch(key) or len(value)>64 or any(ord(c)<32 or ord(c)==127 for c in value):
        raise DeviceError(422,"Invalid label key/value")
    # Serialize writes on this device so concurrent requests cannot exceed 20 labels.
    await db.execute(select(Device.device_id).where(Device.device_id==device_id).with_for_update())
    old = await db.get(DeviceLabel,(device_id,key))
    if old is None and await db.scalar(select(func.count()).select_from(DeviceLabel).where(DeviceLabel.device_id==device_id))>=20:
        raise DeviceError(422,"Maximum 20 labels per device")
    stmt = insert(DeviceLabel).values(device_id=device_id,key=key,value=value)
    await db.execute(stmt.on_conflict_do_update(index_elements=["device_id","key"],set_={"value":value}))
    await db.commit()
    return {"key":key,"value":value}


async def remove_label(db, device_id, user_id, key):
    await check_user_device_access(db,device_id,user_id,required_roles=["Owner","Admin","Manager"])
    if not LABEL_KEY.fullmatch(key):
        raise DeviceError(422,"Invalid label key")
    await db.execute(delete(DeviceLabel).where(DeviceLabel.device_id==device_id,DeviceLabel.key==key))
    await db.commit()
