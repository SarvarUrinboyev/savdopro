-- ====================================================================
--  V23: Price snapshot on stock movements
--
--  The Management / Analytics profit reports historically multiplied SALE
--  movement quantities by the product's CURRENT sale/cost price, so any
--  later price change retroactively rewrote past-period profit. From now
--  on each movement records the unit sale + cost price as it was; reports
--  use the snapshot when present and fall back to the current product
--  price for legacy rows (NULL).
-- ====================================================================
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS unit_sale_price NUMERIC(15, 2);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS unit_cost_price NUMERIC(15, 2);
