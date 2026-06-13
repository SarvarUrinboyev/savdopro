-- =====================================================================
--  V30: Supplier purchase orders + receiving + cost layers (procurement)
--
--  The existing `orders` table is a lightweight "expected delivery" reminder
--  (free-text, one amount, completed flag). This adds a real procurement flow:
--  a purchase order with product line items, partial goods receipt, supplier
--  invoice reference, and a per-receipt cost layer that doubles as the
--  purchase-price history and the FIFO valuation source.
--
--  Costing: each receipt updates the product's weighted-average cost
--  (product.purchase_price) in app code, so all existing COGS / ledger /
--  reports keep working unchanged; the cost layers below enable price history
--  and FIFO valuation on top. Everything is shop-scoped.
-- =====================================================================

-- Purchase order header.
CREATE TABLE purchase_order (
    id             BIGSERIAL PRIMARY KEY,
    shop_id        BIGINT       NOT NULL,
    supplier_id    BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    supplier_name  VARCHAR(180) NOT NULL,
    -- DRAFT | ORDERED | PARTIAL | RECEIVED | CANCELLED
    status         VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    order_date     DATE,
    expected_date  DATE,
    invoice_number VARCHAR(64),
    invoice_date   DATE,
    note           VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_po_shop_status ON purchase_order (shop_id, status);

-- One product line of a purchase order.
CREATE TABLE purchase_order_line (
    id            BIGSERIAL PRIMARY KEY,
    shop_id       BIGINT        NOT NULL,
    po_id         BIGINT        NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    product_id    BIGINT REFERENCES products(id) ON DELETE SET NULL,
    product_name  VARCHAR(200)  NOT NULL,   -- snapshot
    ordered_qty   INTEGER       NOT NULL DEFAULT 0,
    received_qty  INTEGER       NOT NULL DEFAULT 0,
    unit_cost_usd NUMERIC(15,2) NOT NULL DEFAULT 0,
    note          VARCHAR(300),
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_po_line_po      ON purchase_order_line (po_id);
CREATE INDEX idx_po_line_product ON purchase_order_line (product_id);

-- Cost layer: one goods-receipt event for a product. The purchase-price
-- history AND the FIFO valuation source (ordered by receipt_date).
CREATE TABLE purchase_lot (
    id             BIGSERIAL PRIMARY KEY,
    shop_id        BIGINT        NOT NULL,
    product_id     BIGINT        NOT NULL,
    po_id          BIGINT REFERENCES purchase_order(id) ON DELETE SET NULL,
    po_line_id     BIGINT,
    supplier_name  VARCHAR(180),
    receipt_date   DATE          NOT NULL,
    qty            INTEGER       NOT NULL,
    unit_cost_usd  NUMERIC(15,2) NOT NULL,
    invoice_number VARCHAR(64),
    created_at     TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_lot_product ON purchase_lot (product_id, receipt_date);
CREATE INDEX idx_lot_shop    ON purchase_lot (shop_id);
CREATE INDEX idx_lot_po      ON purchase_lot (po_id);
