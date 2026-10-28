-- T3 metric history and labels. Safe to rerun.
ALTER TABLE device_metrics ADD COLUMN IF NOT EXISTS gpu_usage_pct DOUBLE PRECISION;
ALTER TABLE device_metrics ADD COLUMN IF NOT EXISTS cpu_temp_c DOUBLE PRECISION;
ALTER TABLE device_metrics_hourly ADD COLUMN IF NOT EXISTS metric_stats JSONB;
CREATE TABLE IF NOT EXISTS device_metrics_minute (
 device_id INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
 minute_bucket TIMESTAMP NOT NULL, metric_stats JSONB NOT NULL,
 sample_count INT NOT NULL DEFAULT 0,
 PRIMARY KEY(device_id, minute_bucket)
);
CREATE INDEX IF NOT EXISTS ix_metrics_minute_time ON device_metrics_minute(minute_bucket);
CREATE TABLE IF NOT EXISTS device_labels (
 device_id INT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
 key VARCHAR(32) NOT NULL CHECK (key ~ '^[a-z][a-z0-9_]{0,31}$'),
 value VARCHAR(64) NOT NULL,
 PRIMARY KEY(device_id, key)
);
CREATE INDEX IF NOT EXISTS ix_device_labels_filter ON device_labels(key, value, device_id);
