-- ==============================================================================
-- HỆ THỐNG GIÁM SÁT THIẾT BỊ (DEVICE MONITORING SYSTEM)
-- DATABASE: PostgreSQL 15+
-- Phiên bản: 3.1 (Lean) — rút gọn từ 22 bảng xuống 14 bảng
-- ==============================================================================

-- ==============================================================================
-- NHÓM 1: TÀI KHOẢN & PHIÊN ĐĂNG NHẬP
-- ==============================================================================

-- 1. NGƯỜI DÙNG — tự đăng ký; khóa tài khoản khi sai mật khẩu 5 lần
CREATE TABLE users (
    user_id             SERIAL PRIMARY KEY,
    username            VARCHAR(50)  UNIQUE NOT NULL,
    email               VARCHAR(100) UNIQUE NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,          -- bcrypt, cost >= 12
    full_name           VARCHAR(150),
    avatar_url          TEXT,
    is_email_verified   BOOLEAN DEFAULT FALSE,
    failed_login_count  INT DEFAULT 0,                  -- reset về 0 khi đăng nhập đúng
    locked_until        TIMESTAMP,                      -- NOT NULL và > NOW() => đang bị khóa
    last_login_at       TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. TOKEN XÁC THỰC — gộp email_verifications + password_reset_tokens
--    + refresh_tokens + user_fcm_tokens vào MỘT bảng
CREATE TABLE auth_tokens (
    token_id        SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_type      VARCHAR(20) NOT NULL,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,        -- SHA-256 của token thật
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP,                          -- thời điểm đã dùng / bị thu hồi (NULL = còn hiệu lực)
    device_info     VARCHAR(255),                       -- chỉ dùng cho REFRESH
    fcm_token       TEXT,                               -- chỉ dùng cho REFRESH: thiết bị của phiên này nhận push
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_token_type CHECK (token_type IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'REFRESH'))
);

CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id, token_type);

COMMENT ON TABLE auth_tokens IS
    'EMAIL_VERIFY hết hạn 24h | PASSWORD_RESET hết hạn 1h | REFRESH rotate on use (copy fcm_token sang dòng mới). '
    'Danh sách FCM token cần đẩy = các dòng REFRESH còn hiệu lực có fcm_token NOT NULL. Đăng xuất = set used_at.';

-- ==============================================================================
-- NHÓM 2: TỔ CHỨC & PHÂN QUYỀN
-- ==============================================================================

-- 3. TỔ CHỨC
CREATE TABLE organizations (
    org_id          SERIAL PRIMARY KEY,
    org_name        VARCHAR(100) NOT NULL,
    description     TEXT,
    logo_url        TEXT,
    owner_id        INT NOT NULL REFERENCES users(user_id),
    deleted_at      TIMESTAMP,                          -- xóa mềm
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orgs_owner ON organizations(owner_id);

-- 4. THÀNH VIÊN TỔ CHỨC
CREATE TABLE org_members (
    member_id       SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    user_id         INT NOT NULL REFERENCES users(user_id)        ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'Viewer',
    joined_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (org_id, user_id),
    CONSTRAINT chk_org_role CHECK (role IN ('Owner', 'Admin', 'Manager', 'Viewer'))
);

CREATE INDEX idx_org_members_user ON org_members(user_id);

COMMENT ON COLUMN org_members.role IS
    'Owner/Admin thấy toàn bộ cụm. Manager/Viewer chỉ thấy cụm được gán trong user_group_access.';

-- 5. MÃ MỜI — mã 6 ký tự; link và QR dùng chính invite_code
CREATE TABLE org_invitations (
    invite_id       SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    invite_code     VARCHAR(6) UNIQUE NOT NULL,
    default_role    VARCHAR(20) DEFAULT 'Viewer',
    max_uses        INT,                                -- NULL = không giới hạn lượt
    use_count       INT DEFAULT 0,
    expires_at      TIMESTAMP,                          -- NULL = không hết hạn
    revoked_at      TIMESTAMP,                          -- thu hồi lời mời
    created_by      INT REFERENCES users(user_id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_invite_role CHECK (default_role IN ('Manager', 'Viewer'))
);

CREATE INDEX idx_invites_org ON org_invitations(org_id);

-- 6. CỤM THIẾT BỊ
CREATE TABLE device_groups (
    group_id        SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    group_name      VARCHAR(100) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (org_id, group_name)
);

-- 7. PHÂN QUYỀN THEO CỤM (Admin/Owner không cần bảng này)
CREATE TABLE user_group_access (
    access_id       SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id)          ON DELETE CASCADE,
    group_id        INT NOT NULL REFERENCES device_groups(group_id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'Viewer',
    granted_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, group_id),
    CONSTRAINT chk_group_role CHECK (role IN ('Manager', 'Viewer'))
);

-- ==============================================================================
-- NHÓM 3: THIẾT BỊ & DỮ LIỆU GIÁM SÁT
-- ==============================================================================

-- 8. THIẾT BỊ — gộp device_static_info + device_process_snapshots vào đây
CREATE TABLE devices (
    device_id                 SERIAL PRIMARY KEY,
    org_id                    INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    group_id                  INT REFERENCES device_groups(group_id) ON DELETE SET NULL,
    device_name               VARCHAR(100) NOT NULL,
    description               TEXT,
    secret_token_hash         VARCHAR(64) UNIQUE NOT NULL,
    secret_token_prefix       VARCHAR(8)  NOT NULL,
    status                    VARCHAR(20) DEFAULT 'Offline',
    is_monitoring             BOOLEAN DEFAULT TRUE,
    last_heartbeat            TIMESTAMP,
    offline_threshold_minutes INT DEFAULT 5,
    static_info               JSONB,    -- {hostname, os_name, os_version, arch, cpu_model, cpu_cores,
                                        --  ram_total_mb, disk_total_gb, public_ip, local_ip, mac, agent_version}
    top_processes             JSONB,    -- Top 15 tiến trình của lần heartbeat gần nhất:
                                        -- [{pid, name, cpu_pct, ram_mb, user}]
    watch_config              JSONB,    -- Dịch vụ/tiến trình cần theo dõi (Agent đọc khi heartbeat):
                                        -- {"services":  [{"name":"nginx","check_type":"systemd","port":80}],
                                        --  "processes": ["java"]}
    watch_status              JSONB,    -- Kết quả kiểm tra ở heartbeat gần nhất (do Agent tự kiểm tra, không phụ thuộc Top 15):
                                        -- {"services": {"nginx":true}, "processes": {"java":false}}
    archived_at               TIMESTAMP,                -- lưu trữ: giữ lịch sử, ngừng giám sát
    created_by                INT REFERENCES users(user_id),
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_device_status CHECK (status IN ('Online', 'Offline', 'Warning', 'Archived'))
);

CREATE INDEX idx_devices_org       ON devices(org_id);
CREATE INDEX idx_devices_group     ON devices(group_id);
CREATE INDEX idx_devices_heartbeat ON devices(last_heartbeat) WHERE archived_at IS NULL;

COMMENT ON COLUMN devices.org_id IS 'Cô lập dữ liệu theo tổ chức; không phụ thuộc cụm (cụm có thể bị xóa).';

-- 9. DỮ LIỆU HIỆU NĂNG THÔ (HIGH VOLUME — giữ 7 ngày, partition theo tuần)
CREATE TABLE device_metrics (
    metric_id       BIGSERIAL,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    cpu_usage       FLOAT NOT NULL,
    ram_usage       FLOAT NOT NULL,
    disk_usage      FLOAT NOT NULL,
    net_in_kbps     FLOAT DEFAULT 0,
    net_out_kbps    FLOAT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (metric_id, created_at)                 -- created_at bắt buộc trong PK khi partition
) PARTITION BY RANGE (created_at);

CREATE TABLE device_metrics_default PARTITION OF device_metrics DEFAULT;

CREATE INDEX idx_metrics_device_time ON device_metrics(device_id, created_at DESC);

COMMENT ON TABLE device_metrics IS
    'Worker tạo partition tuần tới (create_metrics_partition) và DROP partition quá 7 ngày — không dùng DELETE.';

-- 10. DỮ LIỆU TỔNG HỢP THEO GIỜ (giữ 90 ngày; phục vụ biểu đồ tới 30 ngày)
CREATE TABLE device_metrics_hourly (
    device_id           INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    hour_bucket         TIMESTAMP NOT NULL,
    cpu_avg             FLOAT,
    cpu_max             FLOAT,
    ram_avg             FLOAT,
    ram_max             FLOAT,
    disk_avg            FLOAT,
    net_in_avg_kbps     FLOAT,
    net_out_avg_kbps    FLOAT,
    sample_count        INT DEFAULT 0,
    PRIMARY KEY (device_id, hour_bucket)
);

-- ==============================================================================
-- NHÓM 4: CẢNH BÁO & BẢO TRÌ
-- ==============================================================================

-- 11. QUY TẮC CẢNH BÁO — áp dụng cho một cụm hoặc một thiết bị
CREATE TABLE alert_rules (
    rule_id             SERIAL PRIMARY KEY,
    group_id            INT REFERENCES device_groups(group_id) ON DELETE CASCADE,
    device_id           INT REFERENCES devices(device_id)      ON DELETE CASCADE,
    rule_name           VARCHAR(150),
    metric_type         VARCHAR(20) NOT NULL,           -- CPU, RAM, DISK, NET_IN, NET_OUT, OFFLINE, SERVICE, PROCESS_MISSING
    condition           VARCHAR(5)  NOT NULL DEFAULT '>',
    threshold           FLOAT,                          -- CPU/RAM/DISK/NET: ngưỡng; OFFLINE: số phút mất heartbeat; SERVICE/PROCESS_MISSING: không dùng
    service_name        VARCHAR(100),                   -- bắt buộc với SERVICE (báo khi dịch vụ ngừng chạy)
    process_name        VARCHAR(100),                   -- bắt buộc với PROCESS_MISSING (báo khi tiến trình không còn chạy)
    duration_minutes    INT DEFAULT 1,
    severity            VARCHAR(20) DEFAULT 'WARNING',
    cooldown_minutes    INT DEFAULT 30,
    is_active           BOOLEAN DEFAULT TRUE,
    created_by          INT REFERENCES users(user_id),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rule_scope    CHECK (group_id IS NOT NULL OR device_id IS NOT NULL),
    CONSTRAINT chk_rule_metric   CHECK (metric_type IN ('CPU', 'RAM', 'DISK', 'NET_IN', 'NET_OUT', 'OFFLINE', 'SERVICE', 'PROCESS_MISSING')),
    CONSTRAINT chk_rule_target   CHECK (
        (metric_type = 'SERVICE'         AND service_name IS NOT NULL) OR
        (metric_type = 'PROCESS_MISSING' AND process_name IS NOT NULL) OR
        (metric_type NOT IN ('SERVICE', 'PROCESS_MISSING') AND threshold IS NOT NULL)),
    CONSTRAINT chk_rule_cond     CHECK (condition IN ('>', '>=', '<', '<=')),
    CONSTRAINT chk_rule_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

-- 12. LỊCH SỬ CẢNH BÁO / SỰ CỐ
--     MTTR = AVG(resolved_at - created_at); thời gian phản hồi = acknowledged_at - created_at
CREATE TABLE alert_history (
    alert_id            SERIAL PRIMARY KEY,
    device_id           INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    rule_id             INT REFERENCES alert_rules(rule_id) ON DELETE SET NULL,
    severity            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    metric_value        FLOAT,
    threshold_value     FLOAT,
    acknowledged_by     INT REFERENCES users(user_id),
    acknowledged_at     TIMESTAMP,
    resolved_at         TIMESTAMP,
    notification_status VARCHAR(20) DEFAULT 'PENDING',
    notified_at         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notif_status CHECK (notification_status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_alerts_device     ON alert_history(device_id, created_at DESC);
CREATE INDEX idx_alerts_unresolved ON alert_history(device_id) WHERE resolved_at IS NULL;
CREATE INDEX idx_alerts_notif      ON alert_history(created_at) WHERE notification_status = 'PENDING';

-- 13. LỊCH BẢO TRÌ — trong khung giờ này hệ thống không phát sinh cảnh báo
CREATE TABLE maintenance_windows (
    window_id       SERIAL PRIMARY KEY,
    device_id       INT REFERENCES devices(device_id)       ON DELETE CASCADE,
    group_id        INT REFERENCES device_groups(group_id)  ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP NOT NULL,
    created_by      INT REFERENCES users(user_id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mw_scope CHECK (device_id IS NOT NULL OR group_id IS NOT NULL),
    CONSTRAINT chk_mw_time  CHECK (end_time > start_time)
);

-- ==============================================================================
-- NHÓM 5: ĐIỀU KHIỂN TỪ XA
-- ==============================================================================

-- 14. LỆNH ĐIỀU KHIỂN — đồng thời là lịch sử điều khiển (ai gửi, lý do, kết quả)
CREATE TABLE device_commands (
    command_id      SERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    created_by      INT NOT NULL REFERENCES users(user_id),
    command_type    VARCHAR(50) NOT NULL,
    parameters      JSONB,      -- RESTART_SERVICE: {service_name} | KILL_PROCESS: {pid, process_name} | REBOOT: {delay_seconds}
    reason          TEXT NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',      -- PENDING -> EXECUTING -> SUCCESS / FAILED / TIMEOUT
    output          TEXT,                               -- log hoặc lỗi trả về từ Agent
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT chk_cmd_type   CHECK (command_type IN ('REBOOT', 'RESTART_SERVICE', 'KILL_PROCESS')),
    CONSTRAINT chk_cmd_status CHECK (status IN ('PENDING', 'EXECUTING', 'SUCCESS', 'FAILED', 'TIMEOUT'))
);

CREATE INDEX idx_commands_device  ON device_commands(device_id, created_at DESC);
CREATE INDEX idx_commands_pending ON device_commands(device_id) WHERE status = 'PENDING';

-- ==============================================================================
-- TRIGGER: tự cập nhật updated_at
-- ==============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at         BEFORE UPDATE ON users         FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_organizations_updated_at BEFORE UPDATE ON organizations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_devices_updated_at       BEFORE UPDATE ON devices       FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- FUNCTION: tạo partition tuần cho device_metrics (Worker gọi mỗi Chủ Nhật)
-- Xóa partition cũ do Worker thực hiện bằng DROP TABLE.
-- ==============================================================================
CREATE OR REPLACE FUNCTION create_metrics_partition(week_start DATE)
RETURNS TEXT AS $$
DECLARE
    partition_name TEXT := 'device_metrics_' || TO_CHAR(week_start, 'YYYY_WW');
    week_end       DATE := week_start + 7;
BEGIN
    IF to_regclass(partition_name) IS NOT NULL THEN
        RETURN 'SKIPPED: ' || partition_name || ' already exists';
    END IF;

    EXECUTE FORMAT('CREATE TABLE %I PARTITION OF device_metrics FOR VALUES FROM (%L) TO (%L)',
                   partition_name, week_start::TIMESTAMP, week_end::TIMESTAMP);
    RETURN 'CREATED: ' || partition_name;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- THAM SỐ VẬN HÀNH — chuyển ra file cấu hình ứng dụng (.env / config), không lưu trong DB
--   heartbeat_interval_seconds = 60      offline_threshold_minutes = 5
--   agent_command_jitter_max_sec = 30    process_top_n = 15
--   metrics_retention_days = 7           hourly_retention_days = 90
--   command_history_days = 90            command_timeout_minutes = 5
--   invite_default_expiry_days = 7       max_orgs_per_user = 10
--   max_devices_per_org = 5000           max_devices_system = 50000
-- ==============================================================================
