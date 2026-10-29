-- Keep the newest active custom rule in each scope/component. Retain older history.
WITH ranked AS (
 SELECT rule_id, row_number() OVER (
  PARTITION BY device_id, group_id, metric_type,
   CASE WHEN metric_type='SERVICE' THEN COALESCE(service_name,'')
        WHEN metric_type='PROCESS_MISSING' THEN COALESCE(process_name,'') ELSE '' END
  ORDER BY rule_id DESC
 ) AS position
 FROM alert_rules WHERE is_active AND workspace_id IS NULL
)
UPDATE alert_rules SET is_active=FALSE WHERE rule_id IN (SELECT rule_id FROM ranked WHERE position>1);
DELETE FROM alert_rule_states WHERE rule_id IN (SELECT rule_id FROM alert_rules WHERE NOT is_active);
UPDATE alert_history SET resolved_at=CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
 WHERE resolved_at IS NULL AND rule_id IN (SELECT rule_id FROM alert_rules WHERE NOT is_active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_device_component_active ON alert_rules (
 device_id, metric_type,
 (CASE WHEN metric_type='SERVICE' THEN COALESCE(service_name,'')
       WHEN metric_type='PROCESS_MISSING' THEN COALESCE(process_name,'') ELSE '' END)
) WHERE device_id IS NOT NULL AND is_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_group_component_active ON alert_rules (
 group_id, metric_type,
 (CASE WHEN metric_type='SERVICE' THEN COALESCE(service_name,'')
       WHEN metric_type='PROCESS_MISSING' THEN COALESCE(process_name,'') ELSE '' END)
) WHERE group_id IS NOT NULL AND is_active;
