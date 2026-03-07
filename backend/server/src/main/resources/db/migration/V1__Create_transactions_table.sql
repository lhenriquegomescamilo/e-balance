CREATE TABLE IF NOT EXISTS transactions (
    id          SERIAL PRIMARY KEY,
    operated_at TEXT NOT NULL,
    description TEXT NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    balance     DOUBLE PRECISION NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_unique
ON transactions(operated_at, description, value);

CREATE INDEX IF NOT EXISTS idx_transactions_operated_at
ON transactions(operated_at);
