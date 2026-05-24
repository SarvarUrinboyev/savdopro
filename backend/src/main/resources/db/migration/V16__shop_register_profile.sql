-- =====================================================================
--  V16: per-shop register profile (Phase 3.3)
--
--  Three new optional fields on the shops table so each location can
--  have its own printer, cash register id and receipt footer. Null
--  means "fall back to the OS default" — existing single-shop installs
--  keep working without any config change.
-- =====================================================================

ALTER TABLE shops ADD COLUMN IF NOT EXISTS printer_name     VARCHAR(120);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS cash_register_no VARCHAR(40);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS receipt_footer   VARCHAR(300);
