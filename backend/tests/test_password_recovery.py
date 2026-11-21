"""Exercise account flows against isolated PostgreSQL data and a captured test mailbox."""
import asyncio
import os
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from unittest.mock import patch

import httpx
import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from app.core.config import settings
from app.db.base import Base
from app.db.session import get_db
from app.main import app


@pytest.mark.skipif(os.getenv("TEST_POSTGRES")!="1", reason="requires postgres")
def test_password_lifecycle():
    asyncio.run(exercise())


async def exercise():
    engine=create_async_engine(settings.DATABASE_URL)
    try:
        async with engine.connect() as conn:
            tx=await conn.begin()
            try:
                schema="password_test_"+uuid.uuid4().hex
                await conn.execute(text(f'CREATE SCHEMA "{schema}"'))
                await conn.execute(text(f'SET LOCAL search_path TO "{schema}"'))
                await conn.run_sync(Base.metadata.create_all)
                sql=Path(__file__).resolve().parents[1].joinpath("app/db/password_recovery.sql").read_text()
                for stmt in sql.split(";"):
                    if stmt.strip():
                        await conn.execute(text(stmt))
                async def session():
                    async with AsyncSession(bind=conn,expire_on_commit=False,join_transaction_mode="create_savepoint") as db:
                        yield db
                app.dependency_overrides[get_db]=session
                email="owner@example.com"
                pwd="Original-test-01"
                async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url="http://test/api/v1/auth/") as client:
                    assert (await client.post("register",json=dict(username="tester",email=email,password=pwd))).is_success
                    async def login(password):
                        return await client.post("login",json=dict(identifier=email,password=password))
                    first=(await login(pwd)).json()
                    header={"Authorization":"Bearer "+first["access_token"]}
                    assert (await client.post("change-password",headers=header,json=dict(old_password="incorrect",new_password="Updated-test-02"))).status_code==400
                    assert (await client.post("change-password",headers=header,json=dict(old_password=pwd,new_password="Updated-test-02"))).is_success
                    assert (await client.get("me",headers=header)).status_code==401
                    assert (await client.post("refresh",json=dict(refresh_token=first["refresh_token"]))).status_code==401
                    assert (await login(pwd)).status_code==401
                    assert (await login("Updated-test-02")).is_success
                    with patch("app.api.v1.routers.auth.mail_config",return_value={"test":True}), patch("app.services.password_recovery.SessionLocal",asynccontextmanager(session)), patch("app.services.password_recovery.send_email") as send, patch("app.services.password_recovery.secrets.randbelow",return_value=12345):
                        request=await client.post("forgot-password",json=dict(email=email))
                        unknown=await client.post("forgot-password",json=dict(email="absent@example.com"))
                        assert request.is_success and request.json()==unknown.json()
                        assert "012345" not in request.text
                        await client.post("forgot-password",json=dict(email=email))
                        send.assert_called_once()
                        assert send.call_args.args[0]["recipients"]==[email]
                        assert "012345" in send.call_args.args[2]
                        payload=dict(email=email,reset_code="012345",new_password="Recovered-test-03")
                        for _ in range(5):
                            assert (await client.post("reset-password",json=dict(payload,reset_code="111111"))).status_code==400
                        assert (await client.post("reset-password",json=payload)).status_code==400
                        await conn.execute(text("UPDATE password_recovery SET sent_at=sent_at-interval '16 minutes', attempts=0"))
                        assert (await client.post("reset-password",json=payload)).status_code==400
                        await client.post("forgot-password",json=dict(email=email))
                        assert (await client.post("reset-password",json=payload)).is_success
                        assert (await client.post("reset-password",json=payload)).status_code==400
                        assert (await login("Updated-test-02")).status_code==401
                        assert (await login("Recovered-test-03")).is_success
            finally:
                app.dependency_overrides.pop(get_db,None)
                await tx.rollback()
    finally:
        await engine.dispose()
