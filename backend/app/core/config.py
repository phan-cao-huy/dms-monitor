from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        extra="ignore",
    )

    DATABASE_URL: str = "postgresql+asyncpg://postgresql:123@localhost:5432/dms_monitor"
    JWT_SECRET_KEY: str = "dev-only-change-me"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    EMBEDDED_WORKER: bool = False
    METRICS_RETENTION_DAYS: int = Field(default=7, ge=1)
    HOURLY_RETENTION_DAYS: int = Field(default=90, ge=1)
    FCM_ENABLED: bool = False
    FCM_CREDENTIALS_FILE: str | None = None


settings = Settings()
