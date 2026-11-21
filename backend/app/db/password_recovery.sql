CREATE TABLE IF NOT EXISTS password_recovery (
    user_id integer PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    digest text NOT NULL,
    nonce text NOT NULL,
    sent_at timestamp NOT NULL,
    window_start timestamp NOT NULL,
    deliveries integer NOT NULL,
    attempts integer NOT NULL DEFAULT 0
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_version integer NOT NULL DEFAULT 0;
