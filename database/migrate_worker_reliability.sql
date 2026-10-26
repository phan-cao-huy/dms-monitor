CREATE TABLE IF NOT EXISTS alert_rule_states (
    rule_id INT REFERENCES alert_rules(rule_id) ON DELETE CASCADE,
    device_id INT REFERENCES devices(device_id) ON DELETE CASCADE,
    active_since TIMESTAMP,
    last_seen TIMESTAMP,
    PRIMARY KEY (rule_id, device_id)
);

CREATE OR REPLACE FUNCTION create_metrics_partition(week_start DATE)
RETURNS TEXT AS $$
DECLARE
    start_day DATE := week_start - (EXTRACT(ISODOW FROM week_start)::INT - 1);
    end_day DATE := start_day + 7;
    partition_name TEXT := 'device_metrics_' || TO_CHAR(start_day, 'YYYYMMDD');
BEGIN
    -- Serialize creation with inserts and keep moving DEFAULT rows atomic.
    LOCK TABLE device_metrics IN ACCESS EXCLUSIVE MODE;
    IF to_regclass(partition_name) IS NOT NULL THEN
        RETURN 'SKIPPED: ' || partition_name;
    END IF;
    CREATE TEMP TABLE dms_partition_rows (LIKE device_metrics INCLUDING DEFAULTS) ON COMMIT DROP;
    WITH moved AS (
        DELETE FROM device_metrics_default WHERE created_at >= start_day AND created_at < end_day RETURNING *
    ) INSERT INTO dms_partition_rows SELECT * FROM moved;
    EXECUTE FORMAT('CREATE TABLE %I PARTITION OF device_metrics FOR VALUES FROM (%L) TO (%L)',
                   partition_name, start_day::TIMESTAMP, end_day::TIMESTAMP);
    INSERT INTO device_metrics SELECT * FROM dms_partition_rows;
    DROP TABLE dms_partition_rows;
    RETURN 'CREATED: ' || partition_name;
END;
$$ LANGUAGE plpgsql;
