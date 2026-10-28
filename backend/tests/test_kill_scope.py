import asyncio
from unittest.mock import AsyncMock, Mock, patch

import pytest
from app.models.device import Device
from app.schemas.device import DeviceCommandCreate
from app.services.devices import DeviceError, create_device_command


@pytest.mark.parametrize("name", ["*", "chrome*.exe", "../chrome.exe", "C:\\chrome.exe"])
def test_whole_application_scope_rejects_patterns(name):
    db = Mock(commit=AsyncMock())
    payload = DeviceCommandCreate(command_type="KILL_PROCESS", parameters={"process_name": name}, reason="test")
    with (
        patch("app.services.devices.check_user_device_access", new=AsyncMock(return_value=(Device(device_id=1), None))),
        pytest.raises(DeviceError) as err,
    ):
        asyncio.run(create_device_command(db, 1, 1, payload))
    assert err.value.status_code == 422
    db.commit.assert_not_awaited()


def test_exact_application_scope_is_queued_without_altering_pid_mode():
    db = Mock(commit=AsyncMock(), refresh=AsyncMock())
    payload = DeviceCommandCreate(command_type="KILL_PROCESS", parameters={"process_name": "chrome.exe"}, reason="Close Chrome")
    with patch("app.services.devices.check_user_device_access", new=AsyncMock(return_value=(Device(device_id=1), None))):
        command = asyncio.run(create_device_command(db, 1, 1, payload))
    assert command.parameters == {"process_name": "chrome.exe"}
    assert command.status == "PENDING"
