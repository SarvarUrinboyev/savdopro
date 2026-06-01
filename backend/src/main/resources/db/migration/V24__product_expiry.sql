-- ====================================================================
--  V24: Product expiry date (yaroqlilik muddati)
--
--  Optional per-product best-before / expiry date. Used to warn the
--  operator about goods that are expiring soon or already expired.
--  Nullable — non-perishable goods simply leave it empty.
-- ====================================================================
ALTER TABLE products ADD COLUMN expiry_date DATE;
