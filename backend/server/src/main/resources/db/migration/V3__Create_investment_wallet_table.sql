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

-- ─── Seed: mock portfolio positions ───────────────────────────────────────────
INSERT INTO investment_asset (ticker, name, sector, invested_amount, current_value) VALUES
    ('AAPL',  'Apple Inc.',          'Technology',     5000.0,  6200.0),
    ('MSFT',  'Microsoft Corp.',     'Technology',     4500.0,  5800.0),
    ('GOOGL', 'Alphabet Inc.',       'Technology',     3000.0,  3450.0),
    ('JPM',   'JPMorgan Chase',      'Finance',        3500.0,  3900.0),
    ('BAC',   'Bank of America',     'Finance',        2000.0,  1950.0),
    ('JNJ',   'Johnson & Johnson',   'Healthcare',     2500.0,  2650.0),
    ('PFE',   'Pfizer Inc.',         'Healthcare',     1500.0,  1200.0),
    ('XOM',   'Exxon Mobil',         'Energy',         2000.0,  2400.0),
    ('AMZN',  'Amazon.com Inc.',     'Consumer Disc.', 4000.0,  4800.0),
    ('TSLA',  'Tesla Inc.',          'Consumer Disc.', 3000.0,  2700.0)
ON CONFLICT (ticker) DO NOTHING;

-- ─── Seed: 12 months of sector snapshots (Apr 2025 → Mar 2026) ───────────────
INSERT INTO investment_sector_snapshot (sector_name, month_year, total_value) VALUES
    -- Technology
    ('Technology', '2025-04',  9500.0),
    ('Technology', '2025-05',  9800.0),
    ('Technology', '2025-06', 10200.0),
    ('Technology', '2025-07', 10500.0),
    ('Technology', '2025-08', 10800.0),
    ('Technology', '2025-09', 11000.0),
    ('Technology', '2025-10', 11200.0),
    ('Technology', '2025-11', 12000.0),
    ('Technology', '2025-12', 12800.0),
    ('Technology', '2026-01', 13500.0),
    ('Technology', '2026-02', 14500.0),
    ('Technology', '2026-03', 15450.0),

    -- Finance
    ('Finance', '2025-04', 5100.0),
    ('Finance', '2025-05', 5150.0),
    ('Finance', '2025-06', 5200.0),
    ('Finance', '2025-07', 5250.0),
    ('Finance', '2025-08', 5300.0),
    ('Finance', '2025-09', 5350.0),
    ('Finance', '2025-10', 5400.0),
    ('Finance', '2025-11', 5450.0),
    ('Finance', '2025-12', 5500.0),
    ('Finance', '2026-01', 5600.0),
    ('Finance', '2026-02', 5700.0),
    ('Finance', '2026-03', 5850.0),

    -- Healthcare
    ('Healthcare', '2025-04', 4400.0),
    ('Healthcare', '2025-05', 4350.0),
    ('Healthcare', '2025-06', 4300.0),
    ('Healthcare', '2025-07', 4250.0),
    ('Healthcare', '2025-08', 4200.0),
    ('Healthcare', '2025-09', 4150.0),
    ('Healthcare', '2025-10', 4100.0),
    ('Healthcare', '2025-11', 4050.0),
    ('Healthcare', '2025-12', 4000.0),
    ('Healthcare', '2026-01', 3950.0),
    ('Healthcare', '2026-02', 3900.0),
    ('Healthcare', '2026-03', 3850.0),

    -- Energy
    ('Energy', '2025-04', 1600.0),
    ('Energy', '2025-05', 1650.0),
    ('Energy', '2025-06', 1700.0),
    ('Energy', '2025-07', 1750.0),
    ('Energy', '2025-08', 1800.0),
    ('Energy', '2025-09', 1850.0),
    ('Energy', '2025-10', 1900.0),
    ('Energy', '2025-11', 2000.0),
    ('Energy', '2025-12', 2100.0),
    ('Energy', '2026-01', 2200.0),
    ('Energy', '2026-02', 2300.0),
    ('Energy', '2026-03', 2400.0),

    -- Consumer Disc.
    ('Consumer Disc.', '2025-04', 5800.0),
    ('Consumer Disc.', '2025-05', 5900.0),
    ('Consumer Disc.', '2025-06', 6000.0),
    ('Consumer Disc.', '2025-07', 6100.0),
    ('Consumer Disc.', '2025-08', 6200.0),
    ('Consumer Disc.', '2025-09', 6350.0),
    ('Consumer Disc.', '2025-10', 6500.0),
    ('Consumer Disc.', '2025-11', 6700.0),
    ('Consumer Disc.', '2025-12', 6800.0),
    ('Consumer Disc.', '2026-01', 7000.0),
    ('Consumer Disc.', '2026-02', 7200.0),
    ('Consumer Disc.', '2026-03', 7500.0)
ON CONFLICT (sector_name, month_year) DO NOTHING;
