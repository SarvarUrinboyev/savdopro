-- =====================================================================
--  V33: external Open API (API keys) + outbound webhooks.
--
--  api_keys           — per-integration secrets (SHA-256 hashed) for /api/v1/**.
--  webhook_subscriptions — a shop's registered endpoint URLs + event filter.
--  webhook_deliveries — durable outbox: one row per (event × subscription),
--                       delivered by the scheduled WebhookDispatcher with
--                       retries/backoff. Payload is frozen at enqueue time so
--                       retries are byte-identical and HMAC-signature stable.
--  All shop-scoped (shop_id, no FK — matches sales/payments/audit_log).
-- =====================================================================

CREATE TABLE api_keys (
    id           BIGSERIAL    PRIMARY KEY,
    shop_id      BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL,
    key_prefix   VARCHAR(24)  NOT NULL,
    scopes       VARCHAR(255) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_api_key_hash UNIQUE (key_hash)
);
CREATE INDEX idx_api_keys_shop ON api_keys (shop_id);

CREATE TABLE webhook_subscriptions (
    id         BIGSERIAL    PRIMARY KEY,
    shop_id    BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    secret     VARCHAR(80)  NOT NULL,        -- HMAC signing secret (shown once)
    events     VARCHAR(500) NOT NULL,        -- CSV of subscribed event types
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_subs_shop ON webhook_subscriptions (shop_id, active);

CREATE TABLE webhook_deliveries (
    id              BIGSERIAL    PRIMARY KEY,
    shop_id         BIGINT       NOT NULL,
    subscription_id BIGINT       NOT NULL,
    event_type      VARCHAR(40)  NOT NULL,
    payload_json    TEXT         NOT NULL,
    status          VARCHAR(12)  NOT NULL DEFAULT 'PENDING',  -- PENDING | DELIVERED | FAILED
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP    NOT NULL DEFAULT now(),
    last_error      VARCHAR(500),
    delivered_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
-- The dispatcher polls due work: pending rows whose next attempt time has passed.
CREATE INDEX idx_webhook_deliv_due ON webhook_deliveries (status, next_attempt_at);
CREATE INDEX idx_webhook_deliv_shop ON webhook_deliveries (shop_id, created_at DESC);
