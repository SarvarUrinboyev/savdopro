-- =====================================================================
--  V37: freeze the cost price on each sale line (COGS snapshot).
--
--  Before this, COGS was computed at posting/report time from the
--  product's CURRENT purchase_price, so editing a product's cost later
--  silently rewrote the profit of every past sale of that product.
--  We now snapshot the unit cost onto the sale line at checkout, exactly
--  like unit_price_uzs already snapshots the sale price.
--
--  Backfill: existing lines inherit the product's current purchase_price
--  (the best estimate available for historical rows — it is what the
--  ledger/report already used). Lines whose product was deleted stay NULL;
--  the posting/report code falls back to the current product cost (or 0)
--  for NULL, so behaviour is unchanged for legacy rows.
-- =====================================================================

ALTER TABLE sale_items ADD COLUMN cost_at_sale_uzs NUMERIC(15,2);

UPDATE sale_items
SET cost_at_sale_uzs = (
        SELECT p.purchase_price FROM products p WHERE p.id = sale_items.product_id)
WHERE cost_at_sale_uzs IS NULL
  AND product_id IS NOT NULL;
