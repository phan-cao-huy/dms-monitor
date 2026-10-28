from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class EmailConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")
    host: str = Field(min_length=1, max_length=253, pattern=r"^[a-zA-Z0-9.-]+$")
    port: Literal[465, 587] = 587
    username: str = Field(min_length=1, max_length=255)
    password: str | None = Field(default=None, min_length=1, max_length=1024)
    from_email: EmailStr
    recipients: list[EmailStr] = Field(min_length=1, max_length=20)
    language: Literal["vi", "en"] = "vi"

    @field_validator("username", "password")
    @classmethod
    def no_control_characters(cls, value):
        if value and any(ord(c) < 32 for c in value):
            raise ValueError("Control characters are not allowed")
        return value


class EmailChannelWrite(BaseModel):
    model_config = ConfigDict(extra="forbid")
    name: str = Field(min_length=1, max_length=100)
    enabled: bool = True
    config: EmailConfig
