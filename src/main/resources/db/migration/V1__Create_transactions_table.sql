CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operated_at TEXT NOT NULL,
    description TEXT NOT NULL,
    value REAL NOT NULL,
    balance REAL NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_unique 
ON transactions(operated_at, description, value);

CREATE INDEX IF NOT EXISTS idx_transactions_operated_at 
ON transactions(operated_at);
