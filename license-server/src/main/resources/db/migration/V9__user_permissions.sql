-- Phase 4.5: per-user granular permissions.
-- Stored as a CSV of "RESOURCE:ACTION" tokens (e.g. "USERS:READ,USERS:WRITE")
-- so we don't need a separate join table for what is essentially a small,
-- bounded set per user. NULL = "use the role defaults".

ALTER TABLE app_users ADD COLUMN permissions VARCHAR(500);
