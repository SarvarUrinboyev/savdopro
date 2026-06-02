-- ====================================================================
--  V27: Offline-POS idempotency key (client_ref) on sales
--
--  The POS generates a client_ref (UUID) per checkout. When a sale is
--  queued offline and replayed after reconnect, the backend dedups on
--  client_ref (per shop) and returns the existing sale instead of
--  creating a duplicate — so a lost response can never double-charge.
--  Nullable: ordinary online checkouts may omit it. Uniqueness is
--  enforced in the service layer (portable across H2 and PostgreSQL).
-- ====================================================================
ALTER TABLE sales ADD COLUMN client_ref VARCHAR(64);
CREATE INDEX idx_sales_client_ref ON sales (shop_id, client_ref);
