CREATE TABLE IF NOT EXISTS workspace_alert_retention (
    org_id integer PRIMARY KEY REFERENCES organizations(org_id) ON DELETE CASCADE,
    days integer NOT NULL CHECK (days BETWEEN 1 AND 3650)
);
