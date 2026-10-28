from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Index, Integer, String, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class DeviceMetricMinute(Base):
    __tablename__ = "device_metrics_minute"
    __table_args__ = (Index("ix_metrics_minute_time", "minute_bucket"),)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.device_id", ondelete="CASCADE"), primary_key=True)
    minute_bucket: Mapped[datetime] = mapped_column(DateTime, primary_key=True)
    metric_stats: Mapped[dict] = mapped_column(JSONB)
    sample_count: Mapped[int] = mapped_column(Integer, server_default=text("0"))


class DeviceLabel(Base):
    __tablename__ = "device_labels"
    __table_args__ = (Index("ix_device_labels_filter", "key", "value", "device_id"),)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.device_id", ondelete="CASCADE"), primary_key=True)
    key: Mapped[str] = mapped_column(String(32), primary_key=True)
    value: Mapped[str] = mapped_column(String(64))
