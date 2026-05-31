-- Subscription payments. A row is PENDING when checkout starts and flips to
-- PAID once the PSP webhook confirms it (which extends the account's
-- subscription_expires and sets its plan).
CREATE TABLE IF NOT EXISTS payments (
    id           BIGSERIAL    PRIMARY KEY,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    account_id   BIGINT       NOT NULL,
    plan         VARCHAR(20)  NOT NULL,
    amount_uzs   BIGINT       NOT NULL,
    months       INTEGER      NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    provider     VARCHAR(20),
    external_id  VARCHAR(120),
    paid_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payments_account ON payments (account_id, created_at DESC);
