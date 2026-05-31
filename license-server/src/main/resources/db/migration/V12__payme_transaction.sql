-- Payme (Paycom) Merchant-API transaction state, tracked alongside the payment
-- so CreateTransaction / PerformTransaction / CancelTransaction / CheckTransaction
-- are idempotent. All columns are nullable — Click / MANUAL payments never set them.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_tx_id        VARCHAR(120);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_state        INTEGER;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_create_time  BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_perform_time BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_cancel_time  BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payme_reason       INTEGER;

-- PerformTransaction / CancelTransaction / CheckTransaction look the payment up
-- by Payme's transaction id, so index it.
CREATE INDEX IF NOT EXISTS idx_payments_payme_tx ON payments (payme_tx_id);
