-- =====================================================================
--  V32: persisted AI anomaly-control alerts.
--
--  Deterministic, per-shop anomaly detection (no LLM): every anomaly the
--  AnomalyDetectionService finds is persisted here by AnomalyMonitorService
--  during the scheduled scan. Shop-scoped (shop_id, no FK — matches
--  sales / payments / audit_log).
--
--  (shop_id, dedupe_key) is UNIQUE so re-scanning the same day never
--  inserts a duplicate alert and never re-notifies Telegram. The app also
--  checks via findFirstByShopIdAndDedupeKey; the constraint defends against
--  two concurrent scans racing on the same anomaly.
-- =====================================================================

CREATE TABLE anomaly_alerts (
    id              BIGSERIAL    PRIMARY KEY,
    shop_id         BIGINT       NOT NULL,
    severity        VARCHAR(10)  NOT NULL,            -- info | warn | critical
    code            VARCHAR(40)  NOT NULL,            -- detector tag
    dedupe_key      VARCHAR(120) NOT NULL,            -- stable per (shop, day, detector, subject)
    occurred_on     DATE         NOT NULL,            -- business day the anomaly is about
    message         VARCHAR(500) NOT NULL,            -- deterministic Uzbek text
    detail_json     VARCHAR(2000),                    -- optional structured payload
    acknowledged    BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by VARCHAR(120),
    acknowledged_at TIMESTAMP,
    telegram_sent   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_anomaly_shop_dedupe UNIQUE (shop_id, dedupe_key)
);

CREATE INDEX idx_anomaly_shop_occurred ON anomaly_alerts (shop_id, occurred_on DESC);
CREATE INDEX idx_anomaly_shop_ack      ON anomaly_alerts (shop_id, acknowledged, occurred_on DESC);
