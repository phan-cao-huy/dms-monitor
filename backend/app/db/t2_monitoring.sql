-- T2 monitoring schema. Safe to rerun.

CREATE TABLE IF NOT EXISTS device_events (
	event_pk BIGSERIAL NOT NULL,
	device_id INTEGER NOT NULL,
	log_name VARCHAR(20) NOT NULL,
	record_id BIGINT NOT NULL,
	event_id INTEGER NOT NULL,
	level VARCHAR(20) NOT NULL,
	source VARCHAR(255) NOT NULL,
	message TEXT NOT NULL,
	occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	PRIMARY KEY (event_pk),
	UNIQUE (device_id, log_name, record_id, occurred_at),
	FOREIGN KEY(device_id) REFERENCES devices (device_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_device_events_device_cursor ON device_events (device_id, event_pk);

CREATE INDEX IF NOT EXISTS ix_device_events_device_id ON device_events (device_id);

CREATE INDEX IF NOT EXISTS ix_device_events_occurred_at ON device_events (occurred_at);

CREATE TABLE IF NOT EXISTS synthetic_checks (
	check_id SERIAL NOT NULL,
	org_id INTEGER NOT NULL,
	name VARCHAR(100) NOT NULL,
	type VARCHAR(10) NOT NULL,
	target VARCHAR(2048) NOT NULL,
	interval_seconds INTEGER NOT NULL,
	timeout_seconds INTEGER NOT NULL,
	failure_threshold INTEGER NOT NULL,
	enabled BOOLEAN NOT NULL,
	consecutive_failures INTEGER NOT NULL,
	incident_open BOOLEAN NOT NULL,
	last_checked_at TIMESTAMP WITHOUT TIME ZONE,
	created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
	PRIMARY KEY (check_id),
	FOREIGN KEY(org_id) REFERENCES organizations (org_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_synthetic_checks_org_id ON synthetic_checks (org_id);

CREATE TABLE IF NOT EXISTS check_results (
	result_id BIGSERIAL NOT NULL,
	check_id INTEGER NOT NULL,
	ok BOOLEAN NOT NULL,
	latency_ms FLOAT,
	detail VARCHAR(100) NOT NULL,
	checked_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	PRIMARY KEY (result_id),
	FOREIGN KEY(check_id) REFERENCES synthetic_checks (check_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_check_results_check_cursor ON check_results (check_id, result_id);

CREATE INDEX IF NOT EXISTS ix_check_results_check_id ON check_results (check_id);

CREATE INDEX IF NOT EXISTS ix_check_results_checked_at ON check_results (checked_at);

CREATE TABLE IF NOT EXISTS check_audit (
	audit_id BIGSERIAL NOT NULL,
	org_id INTEGER NOT NULL,
	user_id INTEGER,
	check_id INTEGER,
	action VARCHAR(20) NOT NULL,
	created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
	PRIMARY KEY (audit_id),
	FOREIGN KEY(org_id) REFERENCES organizations (org_id) ON DELETE CASCADE,
	FOREIGN KEY(user_id) REFERENCES users (user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS ix_check_audit_org_id ON check_audit (org_id);

CREATE TABLE IF NOT EXISTS check_incidents (
	incident_id BIGSERIAL NOT NULL,
	check_id INTEGER NOT NULL,
	detail VARCHAR(100) NOT NULL,
	opened_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
	resolved_at TIMESTAMP WITHOUT TIME ZONE,
	PRIMARY KEY (incident_id),
	FOREIGN KEY(check_id) REFERENCES synthetic_checks (check_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_check_incidents_check_id ON check_incidents (check_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_check_open_incident ON check_incidents (check_id) WHERE resolved_at IS NULL;

CREATE TABLE IF NOT EXISTS check_email_deliveries (
	delivery_id BIGSERIAL NOT NULL,
	incident_id BIGINT NOT NULL,
	channel_id INTEGER NOT NULL,
	status VARCHAR(20) NOT NULL,
	attempts INTEGER NOT NULL,
	last_error VARCHAR(100),
	last_attempt_at TIMESTAMP WITHOUT TIME ZONE,
	sent_at TIMESTAMP WITHOUT TIME ZONE,
	PRIMARY KEY (delivery_id),
	UNIQUE (incident_id, channel_id),
	FOREIGN KEY(incident_id) REFERENCES check_incidents (incident_id) ON DELETE CASCADE,
	FOREIGN KEY(channel_id) REFERENCES email_channels (channel_id) ON DELETE CASCADE
);
