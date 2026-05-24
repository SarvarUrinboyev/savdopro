-- =====================================================================
--  V18: payment discount + customer loyalty points (Phase 4.4)
--
--  Discount: a sale (Payment, direction=INCOMING) can record both a
--  percent and/or a flat amount knocked off the gross. The journal
--  still stores `amount` as the net charged — these columns are pure
--  metadata for the receipt + audit trail. Defaults to 0 so existing
--  rows behave unchanged.
--
--  Loyalty: the customer balance is denormalised onto the customers
--  table for fast reads (the points pill on the customer detail page
--  is rendered on every list row, can't pay for a per-row aggregate).
--  Earn / redeem events are appended to customer_transactions with a
--  new `points_delta` column so the ledger stays the single source of
--  truth and the denormalised balance is just a cache.
-- =====================================================================

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS discount_amount  NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5,2)  NOT NULL DEFAULT 0;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS points_balance       BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS points_total_earned  BIGINT NOT NULL DEFAULT 0;

-- Append signed point delta to every relevant ledger row.
-- Earn = positive, redeem = negative. NULL on rows that predate this
-- feature so existing balances stay computable as SUM(COALESCE).
ALTER TABLE customer_transactions
    ADD COLUMN IF NOT EXISTS points_delta BIGINT;

-- Loyalty attribution: which customer this sale belongs to. Null on
-- walk-in sales. Indexed so the customer-detail screen's "last 50 sales"
-- query stays a constant-time lookup.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments (customer_id);
