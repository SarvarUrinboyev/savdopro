-- =====================================================================
--  V3: backfill the created_at column on refresh_tokens
--
--  V2 shipped without created_at (BaseEntity always sets it on persist
--  but the schema validator refuses to start without the column). Some
--  early dev installs ran V2 before the column was added in v3.2,
--  so this migration adds it idempotently — no-op on fresh installs
--  because IF NOT EXISTS short-circuits.
-- =====================================================================

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();
