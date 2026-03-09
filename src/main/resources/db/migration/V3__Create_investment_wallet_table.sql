-- V3: Investment wallet tracking
-- Tables: investment_asset, investment_sector_snapshot

CREATE TABLE IF NOT EXISTS investment_asset (
    id              SERIAL PRIMARY KEY,
    ticker          TEXT    NOT NULL UNIQUE,
    name            TEXT    NOT NULL,
    sector          TEXT    NOT NULL,
    invested_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    current_value   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    notes           TEXT,
    created_at      TEXT    NOT NULL DEFAULT CURRENT_DATE,
    updated_at      TEXT    NOT NULL DEFAULT CURRENT_DATE
);

-- Monthly portfolio value per sector, used for the progress line chart.
-- month_year format: 'YYYY-MM' (sorts lexicographically = chronologically)
CREATE TABLE IF NOT EXISTS investment_sector_snapshot (
    id          SERIAL PRIMARY KEY,
    sector_name TEXT    NOT NULL,
    month_year  TEXT    NOT NULL,
    total_value DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sector_snapshot_uk
    ON investment_sector_snapshot(sector_name, month_year);

