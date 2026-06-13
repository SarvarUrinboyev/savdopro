-- =====================================================================
--  V29: schema catch-up for entity columns that never had a migration.
--
--  A few columns were added to entities over time and created on existing
--  databases by an earlier ddl-auto=update run, but no Flyway migration was
--  ever written for them. A FRESH install (or a fresh test schema) therefore
--  lacks these columns and fails the moment the feature is used:
--
--    • sales.cashier         — POS stamps the cashier on every receipt; the
--                              first checkout fails with "Column cashier not found".
--    • shifts.expected_cash  — cash reconciliation at shift close; reading or
--    • shifts.counted_cash     clearing shifts fails with "Column ... not found".
--
--  Adding them here makes the schema match the entities on every database.
--  IF NOT EXISTS keeps each a no-op where the column already exists (prod) and
--  works on both PostgreSQL and H2 (PostgreSQL mode).
-- =====================================================================

ALTER TABLE sales  ADD COLUMN IF NOT EXISTS cashier       VARCHAR(120);
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS expected_cash NUMERIC(15,2);
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS counted_cash  NUMERIC(15,2);
