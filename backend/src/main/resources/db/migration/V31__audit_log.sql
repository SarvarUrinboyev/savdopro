-- =====================================================================
--  V31: local data-mutation audit trail.
--
--  The license server already audits ADMIN actions; this adds a per-shop
--  trail of WRITE operations against the shop's own data (who changed what,
--  when, from where). Populated best-effort by AuditInterceptor after every
--  mutating /api request. Shop-scoped so an owner sees only their own trail.
-- =====================================================================

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    shop_id    BIGINT       NOT NULL,
    actor      VARCHAR(120),
    method     VARCHAR(10)  NOT NULL,
    path       VARCHAR(300) NOT NULL,
    status     INTEGER      NOT NULL,
    client_ip  VARCHAR(64),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_shop_created ON audit_log (shop_id, created_at DESC);
