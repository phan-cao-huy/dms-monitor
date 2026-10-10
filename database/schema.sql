-- ==============================================================================
-- HỆ THỐNG GIÁM SÁT THIẾT BỊ (DEVICE MONITORING SYSTEM)
-- DATABASE: PostgreSQL 15+
-- Phiên bản: 3.0 (Multi-Tenant Organization Model)
-- Cập nhật: 25/08/2026
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ==============================================================================
-- 1. BẢNG NGƯỜI DÙNG (USERS) — Tự đăng ký, không cần admin tạo
-- ==============================================================================
CREATE TABLE users (
    user_id             SERIAL PRIMARY KEY,
    username            VARCHAR(50)  UNIQUE NOT NULL,
    email               VARCHAR(100) UNIQUE NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(150),
    phone               VARCHAR(20),
    avatar_url          TEXT,
    is_email_verified   BOOLEAN DEFAULT FALSE,
    is_active           BOOLEAN DEFAULT TRUE,
    last_login_at       TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  users                    IS 'Bất kỳ ai cũng có thể tự đăng ký. Không có superadmin toàn cục.';
COMMENT ON COLUMN users.password_hash      IS 'bcrypt hash, cost >= 12. KHÔNG lưu plain text.';
COMMENT ON COLUMN users.is_email_verified  IS 'FALSE cho đến khi người dùng nhấn link xác minh trong email.';

-- ==============================================================================
-- 2. BẢNG XÁC MINH EMAIL (EMAIL VERIFICATIONS)
-- ==============================================================================
CREATE TABLE email_verifications (
    verify_id       SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    is_used         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verify_token ON email_verifications(token_hash) WHERE is_used = FALSE;

COMMENT ON TABLE email_verifications IS 'Token xác minh email. Hết hạn sau 24h.';

-- ==============================================================================
-- 3. BẢNG ĐẶT LẠI MẬT KHẨU (PASSWORD RESET TOKENS)
-- ==============================================================================
CREATE TABLE password_reset_tokens (
    reset_id        SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    is_used         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE password_reset_tokens IS 'Token quên mật khẩu. Hết hạn sau 1h.';

-- ==============================================================================
-- 4. BẢNG REFRESH TOKEN (JWT SESSION MANAGEMENT)
-- ==============================================================================
CREATE TABLE refresh_tokens (
    token_id        SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) UNIQUE NOT NULL,
    device_info     VARCHAR(255),
    ip_address      VARCHAR(45),
    expires_at      TIMESTAMP NOT NULL,
    is_revoked      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 của token thực. Rotate on use.';

-- ==============================================================================
-- 5. BẢNG FCM TOKENS (PUSH NOTIFICATION)
-- ==============================================================================
CREATE TABLE user_fcm_tokens (
    token_id        SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    fcm_token       TEXT NOT NULL,
    device_label    VARCHAR(255),
    app_version     VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP,
    UNIQUE (user_id, fcm_token)
);

CREATE INDEX idx_fcm_user_active ON user_fcm_tokens(user_id) WHERE is_active = TRUE;

-- ==============================================================================
-- 6. BẢNG TỔ CHỨC (ORGANIZATIONS) ⭐ MỚI
-- ==============================================================================
CREATE TABLE organizations (
    org_id          SERIAL PRIMARY KEY,
    org_name        VARCHAR(100) NOT NULL,
    description     TEXT,
    logo_url        TEXT,
    owner_id        INT NOT NULL REFERENCES users(user_id),
    is_active       BOOLEAN DEFAULT TRUE,
    deleted_at      TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orgs_owner ON organizations(owner_id);

COMMENT ON TABLE  organizations          IS 'Không gian làm việc độc lập. Mỗi user có thể tạo và thuộc nhiều org.';
COMMENT ON COLUMN organizations.owner_id IS 'Chủ tổ chức. Người duy nhất có thể xóa Org hoặc chuyển quyền Owner.';

-- ==============================================================================
-- 7. BẢNG THÀNH VIÊN TỔ CHỨC (ORG MEMBERS) ⭐ MỚI
-- ==============================================================================
CREATE TABLE org_members (
    member_id       SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'Viewer',
    -- 'Owner'   : Toàn quyền, tạo ra org
    -- 'Admin'   : Quản lý thành viên, thiết bị, tạo invite
    -- 'Manager' : Quản lý thiết bị trong cụm được giao, gửi lệnh
    -- 'Viewer'  : Chỉ xem (mặc định khi gia nhập qua invite)
    joined_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    invited_by      INT REFERENCES users(user_id),
    invite_code_used VARCHAR(6),
    UNIQUE (org_id, user_id),
    CONSTRAINT chk_org_role CHECK (role IN ('Owner', 'Admin', 'Manager', 'Viewer'))
);

CREATE INDEX idx_org_members_org  ON org_members(org_id);
CREATE INDEX idx_org_members_user ON org_members(user_id);

COMMENT ON TABLE  org_members            IS 'Quan hệ User-Organization. 1 user có thể thuộc nhiều org với role khác nhau.';
COMMENT ON COLUMN org_members.role       IS 'Role cấp Org: Owner/Admin thấy toàn bộ. Manager/Viewer chỉ thấy cụm được gán.';

-- ==============================================================================
-- 8. BẢNG MÃ MỜI GIA NHẬP (ORG INVITATIONS) ⭐ MỚI
-- ==============================================================================
CREATE TABLE org_invitations (
    invite_id       SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,

    -- Mã 6 ký tự hiển thị cho người dùng nhập tay (VD: "A3K9MZ")
    invite_code     VARCHAR(6) UNIQUE NOT NULL,

    -- Token dài hơn dùng cho link/QR (VD: https://dms.app/join/A3K9MZ)
    -- Trong trường hợp này link dùng chính invite_code, token dài dùng làm anti-forgery
    invite_token    VARCHAR(64) UNIQUE NOT NULL,

    default_role    VARCHAR(20) DEFAULT 'Viewer',
    max_uses        INT DEFAULT NULL,        -- NULL = không giới hạn lượt
    use_count       INT DEFAULT 0,
    expires_at      TIMESTAMP DEFAULT NULL,  -- NULL = không hết hạn
    is_active       BOOLEAN DEFAULT TRUE,
    created_by      INT REFERENCES users(user_id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_invite_role CHECK (default_role IN ('Manager', 'Viewer'))
    -- Admin/Owner không được gán qua invite tự động, phải gán thủ công
);

CREATE INDEX idx_invites_code   ON org_invitations(invite_code) WHERE is_active = TRUE;
CREATE INDEX idx_invites_token  ON org_invitations(invite_token) WHERE is_active = TRUE;
CREATE INDEX idx_invites_org    ON org_invitations(org_id);

COMMENT ON TABLE  org_invitations              IS 'Mã mời gia nhập Org. Hỗ trợ 3 hình thức: mã 6 ký tự / link / QR.';
COMMENT ON COLUMN org_invitations.invite_code  IS '6 ký tự [A-Z0-9], VD: A3K9MZ. Người dùng nhập tay hoặc hệ thống parse từ link/QR.';
COMMENT ON COLUMN org_invitations.max_uses     IS 'NULL = dùng không giới hạn. Số > 0 = giới hạn lượt.';
COMMENT ON COLUMN org_invitations.expires_at   IS 'NULL = không hết hạn. Timestamp = hết hạn lúc đó.';

-- ==============================================================================
-- 9. BẢNG CỤM THIẾT BỊ (DEVICE GROUPS) — Thêm org_id
-- ==============================================================================
CREATE TABLE device_groups (
    group_id        SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    group_name      VARCHAR(100) NOT NULL,
    description     TEXT,
    icon            VARCHAR(50)  DEFAULT 'server',
    color           VARCHAR(7)   DEFAULT '#4A90E2',
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_groups_org ON device_groups(org_id);

COMMENT ON TABLE device_groups IS 'Cụm thiết bị thuộc về một Organization. Dữ liệu cô lập theo org_id.';

-- ==============================================================================
-- 10. BẢNG PHÂN QUYỀN THEO CỤM (USER GROUP ACCESS - GROUP-LEVEL RBAC)
-- ==============================================================================
CREATE TABLE user_group_access (
    access_id       SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id)          ON DELETE CASCADE,
    group_id        INT NOT NULL REFERENCES device_groups(group_id)  ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'Viewer',
    granted_by      INT REFERENCES users(user_id),
    granted_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, group_id),
    CONSTRAINT chk_group_role CHECK (role IN ('Manager', 'Viewer'))
);

COMMENT ON TABLE user_group_access IS 'Phân quyền cấp Cụm. Admin-level org không cần bảng này (tự động có quyền tất cả cụm).';

-- ==============================================================================
-- 11. BẢNG THIẾT BỊ (DEVICES)
-- ==============================================================================
CREATE TABLE devices (
    device_id               SERIAL PRIMARY KEY,
    group_id                INT REFERENCES device_groups(group_id) ON DELETE SET NULL,
    device_name             VARCHAR(100) NOT NULL,
    description             TEXT,
    secret_token_hash       VARCHAR(64) UNIQUE NOT NULL,
    secret_token_prefix     VARCHAR(8)  NOT NULL,
    status                  VARCHAR(20) DEFAULT 'Offline',
    last_heartbeat          TIMESTAMP,
    offline_threshold_minutes INT DEFAULT 5,
    deleted_at              TIMESTAMP NULL,
    deleted_by              INT REFERENCES users(user_id),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by              INT REFERENCES users(user_id),
    CONSTRAINT chk_device_status CHECK (status IN ('Online','Offline','Warning','Maintenance','Archived'))
);

CREATE INDEX idx_devices_group     ON devices(group_id);
CREATE INDEX idx_devices_status    ON devices(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_devices_heartbeat ON devices(last_heartbeat) WHERE status != 'Archived';

-- ==============================================================================
-- 12. BẢNG THÔNG TIN TĨNH THIẾT BỊ
-- ==============================================================================
CREATE TABLE device_static_info (
    info_id         SERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE UNIQUE,
    hostname        VARCHAR(255),
    os_name         VARCHAR(100),
    os_version      VARCHAR(100),
    kernel_version  VARCHAR(150),
    arch            VARCHAR(20),
    cpu_model       VARCHAR(255),
    cpu_cores       INT,
    ram_total_mb    BIGINT,
    disk_total_gb   FLOAT,
    public_ip       VARCHAR(45),
    local_ip        VARCHAR(45),
    mac_address     VARCHAR(17),
    agent_version   VARCHAR(20),
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- 13. BẢNG LOG DỮ LIỆU THIẾT BỊ (HIGH VOLUME — GIỮ 7 NGÀY)
--     Thiết kế hỗ trợ Scale: PARTITION BY RANGE(created_at) theo tuần
--     → Xóa dữ liệu cũ bằng DROP PARTITION (microseconds) thay vì DELETE (giờ)
--     → Query theo khoảng thời gian nhanh hơn 10–100 lần (partition pruning)
-- ==============================================================================
CREATE TABLE device_metrics (
    metric_id           BIGSERIAL,
    device_id           INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    cpu_usage           FLOAT NOT NULL,
    cpu_load_1m         FLOAT,
    cpu_load_5m         FLOAT,
    ram_usage           FLOAT NOT NULL,
    ram_used_mb         BIGINT,
    disk_usage          FLOAT NOT NULL,
    disk_used_gb        FLOAT,
    net_in_kbps         FLOAT DEFAULT 0,
    net_out_kbps        FLOAT DEFAULT 0,
    ping_latency_ms     FLOAT,
    uptime_seconds      BIGINT,
    agent_version       VARCHAR(20),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (metric_id, created_at)   -- created_at bắt buộc trong PK khi partition
) PARTITION BY RANGE (created_at);

-- Partition mặc định: chứa dữ liệu không khớp partition nào (an toàn khi tạo partition mới chậm)
CREATE TABLE device_metrics_default PARTITION OF device_metrics DEFAULT;

-- Ví dụ partition theo tuần — Worker tự động tạo và xóa partition theo lịch
-- Worker chạy mỗi Chủ Nhật tạo partition tuần tới + xóa partition > 7 ngày
-- CREATE TABLE device_metrics_2026_w35 PARTITION OF device_metrics
--     FOR VALUES FROM ('2026-08-24 00:00:00') TO ('2026-08-31 00:00:00');

-- Index composite (device_id + created_at) — dùng cho query "metrics của device X trong 24h"
CREATE INDEX idx_metrics_device_time ON device_metrics(device_id, created_at DESC);

-- BRIN Index — cho query quét toàn bộ theo thời gian (nhỏ hơn B-Tree 100 lần)
-- Hiệu quả vì created_at tăng đơn điệu (dữ liệu mới luôn ở cuối bảng)
CREATE INDEX idx_metrics_time_brin ON device_metrics USING BRIN (created_at)
    WITH (pages_per_range = 128);

COMMENT ON TABLE device_metrics IS
    'Bảng HIGH VOLUME: ~150B/record. Partitioned theo tuần để xóa dữ liệu cũ nhanh.'
    ' Small: ~75MB/7ngày | Medium: ~750MB/7ngày | Enterprise: ~7.5GB/7ngày';



-- ==============================================================================
-- 14. BẢNG DỮ LIỆU TỔNG HỢP THEO GIỜ (GIỮ 90 NGÀY)
-- ==============================================================================
CREATE TABLE device_metrics_hourly (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    hour_bucket         TIMESTAMP NOT NULL,
    cpu_avg             FLOAT,
    cpu_max             FLOAT,
    ram_avg             FLOAT,
    ram_max             FLOAT,
    disk_avg            FLOAT,
    net_in_avg_kbps     FLOAT,
    net_out_avg_kbps    FLOAT,
    ping_avg_ms         FLOAT,
    sample_count        INT DEFAULT 0,
    UNIQUE (device_id, hour_bucket)
);

CREATE INDEX idx_metrics_hourly ON device_metrics_hourly(device_id, hour_bucket DESC);

-- ==============================================================================
-- 15. BẢNG SNAPSHOT PROCESS (GIỮ 24 GIỜ)
-- ==============================================================================
CREATE TABLE device_process_snapshots (
    snapshot_id     BIGSERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    processes       JSONB NOT NULL,
    -- Cấu trúc: [{pid, name, exe_path, cmdline, cpu_pct, ram_mb, user, uptime_s}]
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proc_snap_device ON device_process_snapshots(device_id, created_at DESC);

-- ==============================================================================
-- 16. CẤU HÌNH VÀ KẾT QUẢ KIỂM TRA SERVICE
-- ==============================================================================
CREATE TABLE device_service_configs (
    config_id       SERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    service_name    VARCHAR(100) NOT NULL,
    check_type      VARCHAR(20) DEFAULT 'systemd',
    port            INT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE device_service_checks (
    check_id        BIGSERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    service_name    VARCHAR(100) NOT NULL,
    is_running      BOOLEAN NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- 17. BẢNG CẤU HÌNH CẢNH BÁO (ALERT RULES)
-- ==============================================================================
CREATE TABLE alert_rules (
    rule_id             SERIAL PRIMARY KEY,
    org_id              INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    group_id            INT REFERENCES device_groups(group_id) ON DELETE CASCADE,
    device_id           INT REFERENCES devices(device_id) ON DELETE CASCADE,
    rule_name           VARCHAR(150),
    metric_type         VARCHAR(50) NOT NULL,
    -- CPU, RAM, DISK, NET_IN, NET_OUT, PING, OFFLINE, SERVICE, PROCESS_MISSING
    condition           VARCHAR(10) NOT NULL,
    threshold           FLOAT NOT NULL,
    service_name        VARCHAR(100),
    process_name        VARCHAR(100),
    duration_minutes    INT DEFAULT 1,
    severity            VARCHAR(20) DEFAULT 'WARNING',
    cooldown_minutes    INT DEFAULT 30,
    is_active           BOOLEAN DEFAULT TRUE,
    created_by          INT REFERENCES users(user_id),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rule_scope    CHECK (group_id IS NOT NULL OR device_id IS NOT NULL),
    CONSTRAINT chk_rule_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

-- ==============================================================================
-- 18. BẢNG NHẬT KÝ SỰ CỐ (ALERT HISTORY)
-- ==============================================================================
CREATE TABLE alert_history (
    alert_id            SERIAL PRIMARY KEY,
    device_id           INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    rule_id             INT REFERENCES alert_rules(rule_id) ON DELETE SET NULL,
    severity            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    metric_value        FLOAT,
    threshold_value     FLOAT,
    is_resolved         BOOLEAN DEFAULT FALSE,
    resolved_at         TIMESTAMP NULL,
    resolved_by         INT REFERENCES users(user_id),
    resolution_note     TEXT,
    notification_status VARCHAR(20) DEFAULT 'PENDING',
    notified_at         TIMESTAMP,
    notification_error  TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alerts_device     ON alert_history(device_id, created_at DESC);
CREATE INDEX idx_alerts_unresolved ON alert_history(is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX idx_alerts_notif      ON alert_history(notification_status) WHERE notification_status = 'PENDING';

-- ==============================================================================
-- 19. BẢNG CỬA SỔ BẢO TRÌ
-- ==============================================================================
CREATE TABLE maintenance_windows (
    window_id       SERIAL PRIMARY KEY,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    device_id       INT REFERENCES devices(device_id)       ON DELETE CASCADE,
    group_id        INT REFERENCES device_groups(group_id)  ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    created_by      INT REFERENCES users(user_id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mw_scope CHECK (device_id IS NOT NULL OR group_id IS NOT NULL),
    CONSTRAINT chk_mw_time  CHECK (end_time > start_time)
);

-- ==============================================================================
-- 20. BẢNG LỆNH ĐIỀU KHIỂN TỪ XA (DEVICE COMMANDS)
-- ==============================================================================
CREATE TABLE device_commands (
    command_id      SERIAL PRIMARY KEY,
    device_id       INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    org_id          INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
    created_by      INT NOT NULL REFERENCES users(user_id),
    command_type    VARCHAR(50) NOT NULL,
    -- Whitelist: REBOOT, RESTART_SERVICE, KILL_PROCESS
    parameters      JSONB,
    -- REBOOT: {delay_seconds: 10}
    -- RESTART_SERVICE: {service_name: "nginx"}
    -- KILL_PROCESS: {pid: 5678, process_name: "java"}
    reason          TEXT NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING → EXECUTING → SUCCESS / FAILED / TIMEOUT
    output          TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at     TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT chk_cmd_type   CHECK (command_type IN ('REBOOT', 'RESTART_SERVICE', 'KILL_PROCESS')),
    CONSTRAINT chk_cmd_status CHECK (status IN ('PENDING', 'EXECUTING', 'SUCCESS', 'FAILED', 'TIMEOUT'))
);

CREATE INDEX idx_commands_device ON device_commands(device_id, created_at DESC);
CREATE INDEX idx_commands_pending ON device_commands(device_id, status) WHERE status = 'PENDING';

-- ==============================================================================
-- 21. BẢNG NHẬT KÝ KIỂM TOÁN (AUDIT LOGS) — Thêm org_id
-- ==============================================================================
CREATE TABLE audit_logs (
    log_id          BIGSERIAL PRIMARY KEY,
    user_id         INT REFERENCES users(user_id) ON DELETE SET NULL,
    username        VARCHAR(50),
    org_id          INT REFERENCES organizations(org_id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    -- AUTH: REGISTER, LOGIN, LOGOUT, LOGIN_FAILED, EMAIL_VERIFIED
    -- ORG: CREATE_ORG, UPDATE_ORG, DELETE_ORG, TRANSFER_OWNER
    -- INVITE: CREATE_INVITE, REVOKE_INVITE, JOIN_VIA_INVITE
    -- MEMBER: UPDATE_MEMBER_ROLE, REMOVE_MEMBER, LEAVE_ORG
    -- DEVICE: CREATE_DEVICE, UPDATE_DEVICE, ARCHIVE_DEVICE, REGEN_TOKEN
    -- ALERT: CREATE_RULE, UPDATE_RULE, DELETE_RULE, ACKNOWLEDGE_ALERT
    -- COMMAND: SEND_COMMAND, COMMAND_EXECUTED, COMMAND_FAILED
    entity_type     VARCHAR(50),
    entity_id       INT,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_org    ON audit_logs(org_id, created_at DESC);
CREATE INDEX idx_audit_user   ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs(action, created_at DESC);

COMMENT ON TABLE audit_logs IS 'KHÔNG BAO GIỜ xóa. Dữ liệu kiểm toán và điều tra sự cố.';

-- ==============================================================================
-- 22. BẢNG CẤU HÌNH HỆ THỐNG
-- ==============================================================================
CREATE TABLE system_settings (
    key             VARCHAR(100) PRIMARY KEY,
    value           TEXT NOT NULL,
    description     TEXT,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_settings (key, value, description) VALUES
    -- Tham số Agent
    ('heartbeat_interval_seconds',      '60',       'Tần suất agent gửi dữ liệu (giây)'),
    ('offline_threshold_minutes',       '5',        'Mặc định: bao nhiêu phút không heartbeat thì Offline'),
    ('agent_command_jitter_max_sec',    '30',       'Jitter tối đa Agent chờ trước khi bắt đầu (chống thundering herd)'),

    -- Data Retention (NFR013–NFR016)
    ('metrics_retention_days',          '7',        'Giữ dữ liệu raw device_metrics bao nhiêu ngày'),
    ('hourly_retention_days',           '90',       'Giữ dữ liệu device_metrics_hourly bao nhiêu ngày'),
    ('process_snapshot_hours',          '24',       'Giữ device_process_snapshots bao nhiêu giờ'),
    ('command_history_days',            '90',       'Giữ device_commands history bao nhiêu ngày'),
    ('audit_log_retention_days',        '3650',     'Giữ audit_logs bao nhiêu ngày (10 năm — không nên xóa)'),

    -- Command Queue
    ('command_timeout_minutes',         '5',        'Sau bao lâu lệnh PENDING/EXECUTING tự chuyển TIMEOUT'),
    ('command_max_retry',               '0',        'Số lần retry lệnh thất bại (0 = không retry)'),

    -- Invite System
    ('invite_code_length',              '6',        'Độ dài mã mời (ký tự) — mặc định 6'),
    ('invite_default_expiry_days',      '7',        'Thời gian hết hạn mặc định của Invite (ngày)'),
    ('max_orgs_per_user',               '10',       'Số lượng org tối đa 1 user có thể tạo'),

    -- Capacity Planning (tham chiếu NFR)
    ('max_devices_per_org',             '5000',     'Giới hạn thiết bị tối đa 1 org (NFR018)'),
    ('max_devices_system',              '50000',    'Giới hạn thiết bị tối đa toàn hệ thống'),
    ('db_partition_create_days_ahead',  '7',        'Tạo partition mới trước bao nhiêu ngày'),
    ('process_top_n',                   '15',       'Số lượng process tối đa thu thập mỗi Heartbeat');

-- ==============================================================================
-- TRIGGERS: Tự động cập nhật updated_at
-- ==============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at          BEFORE UPDATE ON users           FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_organizations_updated_at  BEFORE UPDATE ON organizations   FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_device_groups_updated_at  BEFORE UPDATE ON device_groups   FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_devices_updated_at        BEFORE UPDATE ON devices         FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_alert_rules_updated_at    BEFORE UPDATE ON alert_rules     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- FUNCTION: Sinh mã invite 6 ký tự ngẫu nhiên (A-Z, 0-9)
-- ==============================================================================
CREATE OR REPLACE FUNCTION generate_invite_code()
RETURNS VARCHAR(6) AS $$
DECLARE
    chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    -- Loại bỏ: I, O, 0, 1 để tránh nhầm lẫn khi đọc/nhập tay
    -- Không gian: 32^6 = 1,073,741,824 (~1 tỷ) tổ hợp (NFR030)
    result VARCHAR(6) := '';
    i INTEGER;
BEGIN
    FOR i IN 1..6 LOOP
        result := result || substr(chars, floor(random() * length(chars) + 1)::integer, 1);
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION generate_invite_code IS 'Sinh mã 6 ký tự từ [A-Z 0-9] trừ I, O, 0, 1 để tránh nhầm lẫn thị giác. ~1 tỷ tổ hợp.';

-- ==============================================================================
-- FUNCTION: Tạo Partition device_metrics cho 1 tuần cụ thể
-- Gọi bởi Partition Manager Worker mỗi Chủ Nhật
-- ==============================================================================
CREATE OR REPLACE FUNCTION create_metrics_partition(week_start DATE)
RETURNS TEXT AS $$
DECLARE
    partition_name TEXT;
    week_end DATE;
    sql_stmt TEXT;
BEGIN
    week_end := week_start + INTERVAL '7 days';
    partition_name := 'device_metrics_' || TO_CHAR(week_start, 'YYYY_WW');

    -- Kiểm tra partition đã tồn tại chưa
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = partition_name AND n.nspname = 'public'
    ) THEN
        RETURN 'SKIPPED: ' || partition_name || ' already exists';
    END IF;

    sql_stmt := FORMAT(
        'CREATE TABLE %I PARTITION OF device_metrics FOR VALUES FROM (%L) TO (%L)',
        partition_name,
        week_start::TIMESTAMP,
        week_end::TIMESTAMP
    );

    EXECUTE sql_stmt;
    RETURN 'CREATED: ' || partition_name || ' (' || week_start || ' to ' || week_end || ')';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_metrics_partition IS
    'Tạo partition tuần cho device_metrics. Worker gọi hàm này mỗi Chủ Nhật cho tuần tiếp theo.';

-- ==============================================================================
-- FUNCTION: Xóa Partition device_metrics cũ hơn N ngày
-- Gọi bởi Data Retention Worker
-- ==============================================================================
CREATE OR REPLACE FUNCTION drop_old_metrics_partitions(retention_days INT DEFAULT 7)
RETURNS SETOF TEXT AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE;
    result_msg TEXT;
BEGIN
    cutoff_date := CURRENT_DATE - retention_days;

    FOR partition_record IN
        SELECT
            c.relname AS partition_name,
            pg_get_expr(c.relpartbound, c.oid) AS partition_bound
        FROM pg_class c
        JOIN pg_inherits i ON i.inhrelid = c.oid
        JOIN pg_class p ON p.oid = i.inhparent
        WHERE p.relname = 'device_metrics'
          AND c.relname != 'device_metrics_default'
          AND c.relname LIKE 'device_metrics_%'
    LOOP
        -- Kiểm tra nếu partition chứa dữ liệu trước cutoff_date thì xóa
        -- Đơn giản: parse tên partition để lấy ngày (device_metrics_YYYY_WW)
        -- Worker Python sẽ parse và gọi DROP TABLE trực tiếp
        -- Ở đây log để audit
        result_msg := 'CHECK: ' || partition_record.partition_name
                      || ' | ' || partition_record.partition_bound;
        RETURN NEXT result_msg;
    END LOOP;

    RETURN;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION drop_old_metrics_partitions IS
    'Liệt kê các partition để kiểm tra. Worker Python thực hiện DROP TABLE thực sự.'
    ' DROP PARTITION hoàn thành trong microseconds, không lock table chính.';

-- ==============================================================================
-- VIEW: Xem kích thước từng bảng và partition (hỗ trợ capacity monitoring)
-- ==============================================================================
CREATE OR REPLACE VIEW v_table_sizes AS
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname || '.' || tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)
                   - pg_relation_size(schemaname || '.' || tablename)) AS index_size,
    (SELECT reltuples::BIGINT FROM pg_class WHERE relname = tablename) AS estimated_rows
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname || '.' || tablename) DESC;

COMMENT ON VIEW v_table_sizes IS
    'Xem dung lượng từng bảng. SELECT * FROM v_table_sizes để monitor capacity.';

