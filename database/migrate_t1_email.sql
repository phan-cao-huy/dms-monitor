-- T1 email channels and workspace default rules. Safe to rerun.
CREATE TABLE IF NOT EXISTS email_channels (
 channel_id SERIAL PRIMARY KEY, org_id INT NOT NULL REFERENCES organizations(org_id) ON DELETE CASCADE,
 name VARCHAR(100) NOT NULL, config_encrypted TEXT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 last_test_at TIMESTAMP, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_email_channels_org_id ON email_channels(org_id);
CREATE TABLE IF NOT EXISTS email_deliveries (
 delivery_id SERIAL PRIMARY KEY, alert_id INT NOT NULL REFERENCES alert_history(alert_id) ON DELETE CASCADE,
 channel_id INT NOT NULL REFERENCES email_channels(channel_id) ON DELETE CASCADE,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
 attempts INT NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3), last_error VARCHAR(100),
 last_attempt_at TIMESTAMP, sent_at TIMESTAMP, UNIQUE(alert_id, channel_id)
);
CREATE INDEX IF NOT EXISTS ix_email_deliveries_alert_id ON email_deliveries(alert_id);
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS workspace_id INT REFERENCES organizations(org_id) ON DELETE CASCADE;
ALTER TABLE alert_rules ADD COLUMN IF NOT EXISTS default_key VARCHAR(30);
CREATE UNIQUE INDEX IF NOT EXISTS uq_workspace_default_rule ON alert_rules(workspace_id, default_key);
ALTER TABLE alert_rules DROP CONSTRAINT IF EXISTS chk_rule_scope;
ALTER TABLE alert_rules ADD CONSTRAINT chk_rule_scope CHECK (
 (workspace_id IS NOT NULL AND default_key IS NOT NULL AND device_id IS NULL AND group_id IS NULL)
 OR (workspace_id IS NULL AND (device_id IS NOT NULL OR group_id IS NOT NULL))
);
ALTER TABLE alert_rules DROP CONSTRAINT IF EXISTS chk_rule_metric;
ALTER TABLE alert_rules ADD CONSTRAINT chk_rule_metric CHECK (metric_type IN ('CPU','RAM','GPU','DISK','NET_IN','NET_OUT','OFFLINE','SERVICE','PROCESS_MISSING','CPU_TEMP','DISK_HEALTH'));
INSERT INTO alert_rules(workspace_id, default_key, rule_name, metric_type, condition, threshold, duration_minutes, severity)
SELECT o.org_id, t.metric, t.name, t.metric, '>', t.threshold, t.duration, t.severity
FROM organizations o CROSS JOIN (VALUES
 ('CPU','CPU > 90%',90.0,5,'WARNING'),
 ('RAM','RAM > 90%',90.0,5,'WARNING'),
 ('DISK','Disk > 90%',90.0,1,'WARNING'),
 ('OFFLINE','Offline (device setting)',5.0,1,'CRITICAL'),
 ('CPU_TEMP','CPU temperature > 85 C',85.0,1,'WARNING')
) AS t(metric,name,threshold,duration,severity)
WHERE o.deleted_at IS NULL
ON CONFLICT (workspace_id, default_key) DO NOTHING;
