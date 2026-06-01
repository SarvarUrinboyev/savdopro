-- ====================================================================
--  V26: Online payments (Click / Payme) for customer debt repayment
--
--  Records every online payment attempt so the provider callbacks
--  (Payme JSON-RPC, Click prepare/complete) are idempotent and auditable.
--  NOT tenant-filtered: the provider's servers call the webhooks with no
--  shop context, so rows are addressed by absolute id and carry shop_id
--  only for reporting. A successful PerformTransaction / Complete writes
--  one PAYMENT row into customer_transactions (ledger_tx_id below).
--
--    state:  0 = created/prepared, 2 = performed (paid),
--           -1 = cancelled before perform, -2 = cancelled after perform
-- ====================================================================
CREATE TABLE online_payments (
    id               BIGSERIAL PRIMARY KEY,
    provider         VARCHAR(16)  NOT NULL,        -- PAYME | CLICK
    provider_txn_id  VARCHAR(64),                  -- Payme _id / Click click_trans_id
    customer_id      BIGINT       NOT NULL,
    shop_id          BIGINT,
    amount           NUMERIC(15,2) NOT NULL,
    state            INT          NOT NULL DEFAULT 0,
    create_time_ms   BIGINT,
    perform_time_ms  BIGINT,
    cancel_time_ms   BIGINT,
    reason           INT,
    ledger_tx_id     BIGINT,                        -- the customer_transactions PAYMENT row
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP
);

CREATE INDEX idx_online_payments_provider_txn ON online_payments (provider, provider_txn_id);
CREATE INDEX idx_online_payments_customer     ON online_payments (customer_id);
