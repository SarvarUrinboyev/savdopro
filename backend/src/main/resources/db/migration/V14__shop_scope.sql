-- ====================================================================
--  Shop scoping (Phase 1C-2: data isolation by shop_id)
--
--  Every transactional table gets a NOT NULL shop_id FK pointing at
--  shops(id). Existing rows are backfilled with the bootstrap super-
--  admin's main shop (id 1) — at this point the desktop install has
--  always been single-tenant so this is correct. New rows are written
--  with the shop_id picked from the X-Shop-Id request header
--  (TenantContext on the backend) and read queries filter by it via
--  the Hibernate @Filter "tenantFilter".
-- ====================================================================

-- Add shop_id with a temporary DEFAULT so existing rows backfill in
-- one go; we drop the default afterwards so future inserts must
-- explicitly set the column (caught by the service layer).
ALTER TABLE shifts                  ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE day_balance             ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE expenses                ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE home_expenses           ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE orders                  ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE debtors                 ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE customer_debts          ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE debt_payments           ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE terminal_balances       ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE products                ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE categories              ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE stock_movements         ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE customers               ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE customer_transactions   ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE management_costs        ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE payments                ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);
ALTER TABLE suppliers               ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 REFERENCES shops(id);

-- Drop defaults so new code is forced to set the column explicitly.
ALTER TABLE shifts                  ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE day_balance             ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE expenses                ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE home_expenses           ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE orders                  ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE debtors                 ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE customer_debts          ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE debt_payments           ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE terminal_balances       ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE products                ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE categories              ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE stock_movements         ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE customers               ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE customer_transactions   ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE management_costs        ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE payments                ALTER COLUMN shop_id DROP DEFAULT;
ALTER TABLE suppliers               ALTER COLUMN shop_id DROP DEFAULT;

-- Indexes for the per-tenant scan that dominates every read path.
CREATE INDEX idx_shifts_shop                ON shifts                (shop_id);
CREATE INDEX idx_day_balance_shop           ON day_balance           (shop_id);
CREATE INDEX idx_expenses_shop              ON expenses              (shop_id);
CREATE INDEX idx_home_expenses_shop         ON home_expenses         (shop_id);
CREATE INDEX idx_orders_shop                ON orders                (shop_id);
CREATE INDEX idx_debtors_shop               ON debtors               (shop_id);
CREATE INDEX idx_customer_debts_shop        ON customer_debts        (shop_id);
CREATE INDEX idx_debt_payments_shop         ON debt_payments         (shop_id);
CREATE INDEX idx_terminal_balances_shop     ON terminal_balances     (shop_id);
CREATE INDEX idx_products_shop              ON products              (shop_id);
CREATE INDEX idx_categories_shop            ON categories            (shop_id);
CREATE INDEX idx_stock_movements_shop       ON stock_movements       (shop_id);
CREATE INDEX idx_customers_shop             ON customers             (shop_id);
CREATE INDEX idx_customer_transactions_shop ON customer_transactions (shop_id);
CREATE INDEX idx_management_costs_shop      ON management_costs      (shop_id);
CREATE INDEX idx_payments_shop              ON payments              (shop_id);
CREATE INDEX idx_suppliers_shop             ON suppliers             (shop_id);
