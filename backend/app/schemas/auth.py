from pydantic import BaseModel, EmailStr, Field, field_validator


class RegisterIn(BaseModel):
    username: str = Field(min_length=3, max_length=50, pattern=r"^[A-Za-z0-9_.-]+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=72)
    full_name: str | None = Field(default=None, max_length=150)

    @field_validator("password")
    @classmethod
    def password_must_fit_bcrypt(cls, value: str) -> str:
        if len(value.encode("utf-8")) > 72:
            raise ValueError("Password must be at most 72 UTF-8 bytes")
        return value


class LoginIn(BaseModel):
    identifier: str = Field(min_length=1, max_length=100)
    password: str = Field(min_length=1, max_length=72)


class RefreshIn(BaseModel):
    refresh_token: str = Field(min_length=1)


class FcmTokenIn(BaseModel):
    fcm_token: str = Field(min_length=1)
    device_label: str | None = Field(default=None, max_length=255)
    app_version: str | None = Field(default=None, max_length=20)


class UserOut(BaseModel):
    user_id: int
    username: str
    email: EmailStr
    full_name: str | None


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
