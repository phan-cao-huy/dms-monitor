from pathlib import Path

from typing import Annotated, Literal
from pydantic import Field, SecretStr, BeforeValidator
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
    MINUTE_RETENTION_DAYS: int = Field(default=30, ge=3)
    HOURLY_RETENTION_DAYS: int = Field(default=90, ge=1)
    NOTIFICATION_ENCRYPTION_KEY: str | None = None
    EVENTS_RETENTION_DAYS: int = Field(default=7, ge=1, le=365)
    SYNTHETIC_ALLOWED_CIDRS: str = ""
    FCM_ENABLED: bool = False
    FCM_CREDENTIALS_FILE: str | None = None
    RESET_SMTP_HOST: str = "smtp.gmail.com"
    RESET_SMTP_PORT: Annotated[Literal[465, 587], BeforeValidator(int)] = 587
    RESET_SMTP_USERNAME: str = ""
    RESET_SMTP_PASSWORD: SecretStr | None = None
    RESET_SMTP_FROM: str = ""
    GOOGLE_CLIENT_ID: str = "455891485973-rfehjhelt6q9e0nfqhcv5qpgci3q3at2.apps.googleusercontent.com"


settings = Settings()
