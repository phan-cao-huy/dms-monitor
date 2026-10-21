from collections.abc import Awaitable
from typing import Annotated, TypeVar

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.deps import get_current_user
from app.db.session import get_db
from app.models.alert import AlertHistory, AlertRule
from app.models.auth import User
from app.schemas.alert import AlertHistoryOut, AlertRuleCreate, AlertRuleOut
from app.services.alerts import (
    AlertError,
    acknowledge_alert,
    create_alert_rule,
    delete_alert_rule,
    list_alert_rules,
    list_org_alerts,
    resolve_alert,
)

router = APIRouter(tags=["alerts"])
Result = TypeVar("Result")


async def _run(operation: Awaitable[Result]) -> Result:
    try:
        return await operation
    except AlertError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail=exc.detail,
        ) from exc


@router.get(
    "/orgs/{org_id}/alert-rules",
    response_model=list[AlertRuleOut],
    summary="Liệt kê quy tắc cảnh báo",
)
async def get_alert_rules(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> list[AlertRule]:
    """Liệt kê các quy tắc cảnh báo trong tổ chức."""
    return await _run(list_alert_rules(db, org_id, user.user_id))


@router.post(
    "/orgs/{org_id}/alert-rules",
    response_model=AlertRuleOut,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo quy tắc cảnh báo",
)
async def post_alert_rule(
    org_id: int,
    payload: AlertRuleCreate,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> AlertRule:
    """Tạo quy tắc cảnh báo mới áp dụng cho một cụm hoặc một thiết bị."""
    return await _run(create_alert_rule(db, org_id, user.user_id, payload))


@router.delete(
    "/alert-rules/{rule_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Xóa quy tắc cảnh báo",
)
async def delete_alert_rule_endpoint(
    rule_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> Response:
    """Xóa một quy tắc cảnh báo."""
    await _run(delete_alert_rule(db, rule_id, user.user_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get(
    "/orgs/{org_id}/alerts",
    response_model=list[AlertHistoryOut],
    summary="Lấy lịch sử cảnh báo",
)
async def get_org_alerts(
    org_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    resolved: bool | None = Query(default=None, description="Lọc theo trạng thái đã giải quyết"),
    limit: int = Query(default=100, ge=1, le=500),
) -> list[AlertHistory]:
    """Lấy danh sách các cảnh báo / sự cố trong tổ chức."""
    return await _run(list_org_alerts(db, org_id, user.user_id, resolved, limit))


@router.post(
    "/alerts/{alert_id}/resolve",
    response_model=AlertHistoryOut,
    summary="Xác nhận đã giải quyết cảnh báo",
)
async def post_resolve_alert(
    alert_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> AlertHistory:
    """Đánh dấu cảnh báo đã được xử lý xong."""
    return await _run(resolve_alert(db, alert_id, user.user_id))


@router.post(
    "/alerts/{alert_id}/acknowledge",
    response_model=AlertHistoryOut,
    summary="Tiếp nhận xử lý cảnh báo",
)
async def post_acknowledge_alert(
    alert_id: int,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
) -> AlertHistory:
    """Đánh dấu cảnh báo đã được tiếp nhận bởi người dùng hiện tại."""
    return await _run(acknowledge_alert(db, alert_id, user.user_id))
