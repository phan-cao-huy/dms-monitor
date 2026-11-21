CREATE TABLE IF NOT EXISTS storage_cleanup (
    id integer PRIMARY KEY CHECK (id = 1),
    status text NOT NULL,
    updated_at timestamptz NOT NULL
);
