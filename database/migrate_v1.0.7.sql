-- ==============================================================================
-- DMS MONITOR - MIGRATION v1.0.7
-- Thêm cột is_monitoring và cập nhật CHECK constraints cho status và command_type
-- ==============================================================================

-- 1. Thêm cột is_monitoring nếu chưa có
ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_monitoring BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Cập nhật constraint chk_device_status để hỗ trợ trạng thái 'Paused'
ALTER TABLE devices DROP CONSTRAINT IF EXISTS chk_device_status;
ALTER TABLE devices DROP CONSTRAINT IF EXISTS devices_status_check;
ALTER TABLE devices ADD CONSTRAINT chk_device_status CHECK (status IN ('Online', 'Offline', 'Warning', 'Archived', 'Paused'));

-- 3. Cập nhật constraint chk_cmd_type để hỗ trợ lệnh 'PAUSE_MONITORING', 'RESUME_MONITORING', 'UNINSTALL'
ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS chk_cmd_type;
ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS device_commands_command_type_check;
ALTER TABLE device_commands ADD CONSTRAINT chk_cmd_type CHECK (command_type IN ('REBOOT', 'RESTART_SERVICE', 'KILL_PROCESS', 'PAUSE_MONITORING', 'RESUME_MONITORING', 'UNINSTALL'));
