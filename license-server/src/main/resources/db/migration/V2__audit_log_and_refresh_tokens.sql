-- =====================================================================
--  V2: super-admin audit log + refresh-token persistence
--
--  Audit log: every admin write (create / update / block / delete /
--  password reset / user CRUD) is appended here. Read-only after insert;
--  the panel queries it in reverse chronological order.
--
--  Refresh tokens: short-lived access tokens (1h) are paired with a
--  long-lived refresh token (7d) stored as a SHA-256 hash here so a DB
--  leak doesn't trivially impersonate users. The actor_user_id /
--  account_id columns let us revoke every token for a user when an
--  account is blocked.
-- =====================================================================

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT       NOT NULL,
    actor_name    VARCHAR(80)  NOT NULL,        -- denormalised so the row survives user deletion
    action        VARCHAR(60)  NOT NULL,        -- e.g. ACCOUNT_CREATE, USER_RESET_PASSWORD
    target_type   VARCHAR(20)  NOT NULL,        -- ACCOUNT | USER | TOKEN
    target_id     BIGINT,                       -- nullable for bulk / system events
    target_label  VARCHAR(180),                 -- human-readable: account name, username, etc.
    detail        VARCHAR(500),                 -- free-form context, never sensitive data
    client_ip     VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_created  ON admin_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor    ON admin_audit_log (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_target   ON admin_audit_log (target_type, target_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    -- SHA-256 of the raw refresh token. Indexed UNIQUE so we can look
    -- a token up in O(1) without storing the plaintext server-side.
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    user_id         BIGINT       NOT NULL,
    account_id      BIGINT       NOT NULL,
    issued_at       TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP    NOT NULL,
    revoked_at      TIMESTAMP,                  -- null = still valid
    last_used_at    TIMESTAMP,
    client_ip       VARCHAR(64),
    -- BaseEntity's @PrePersist sets created_at on every row — this
    -- column must exist or the schema validator refuses to start.
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_user    ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_expires ON refresh_tokens (expires_at);
