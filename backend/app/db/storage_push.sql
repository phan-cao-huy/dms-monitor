CREATE TABLE IF NOT EXISTS storage_push_state (
    user_id integer PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    level text NOT NULL,
    sent_at timestamptz NOT NULL DEFAULT now()
);
