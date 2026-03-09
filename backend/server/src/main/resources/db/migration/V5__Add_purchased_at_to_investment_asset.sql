-- V5: Add purchase date to investment_asset for P&L tracking over time.
-- NULL allowed so existing rows are unaffected; format: 'YYYY-MM-DD'
ALTER TABLE investment_asset
    ADD COLUMN IF NOT EXISTS purchased_at TEXT;
