-- =====================================================================
--  V17: cross-shop stock transfers (Phase 3.3)
--
--  A transfer moves N units of one product from shop A to shop B in the
--  same account. The row is the audit trail; the actual stock change is
--  applied transactionally by the application layer (two updates on
--  products, two rows in stock_movements).
--
--  Unlike most tables this one is NOT shop-scoped — it bridges two
--  shops. We use account_id so the tenant filter still works (a row
--  belongs to whichever account the source shop belongs to).
-- =====================================================================

CREATE TABLE IF NOT EXISTS shop_transfers (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT       NOT NULL,
    from_shop_id    BIGINT       NOT NULL REFERENCES shops(id),
    to_shop_id      BIGINT       NOT NULL REFERENCES shops(id),
    -- Product id from the source shop. Resolution to the destination
    -- product happens by barcode match at application level (or by
    -- auto-create if no match). Stored here for the audit trail.
    source_product_id   BIGINT,
    dest_product_id     BIGINT,
    product_name        VARCHAR(180) NOT NULL,
    product_barcode     VARCHAR(80),
    qty             NUMERIC(15,3) NOT NULL,
    note            VARCHAR(500),
    created_by      VARCHAR(120),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfer_diff_shops CHECK (from_shop_id <> to_shop_id),
    CONSTRAINT chk_transfer_qty_pos    CHECK (qty > 0)
);

CREATE INDEX IF NOT EXISTS idx_transfer_account ON shop_transfers (account_id);
CREATE INDEX IF NOT EXISTS idx_transfer_from    ON shop_transfers (from_shop_id);
CREATE INDEX IF NOT EXISTS idx_transfer_to      ON shop_transfers (to_shop_id);
CREATE INDEX IF NOT EXISTS idx_transfer_created ON shop_transfers (created_at DESC);
