-- Add the stock exchange identifier used for SerpAPI queries (e.g. NASDAQ, NYSE, EURONEXT)
ALTER TABLE investment_asset ADD COLUMN exchange TEXT NOT NULL DEFAULT 'NASDAQ';
