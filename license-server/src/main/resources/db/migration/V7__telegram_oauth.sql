-- Phase 4.5: Telegram Login Widget linkage.
-- One Telegram account binds to at most one SavdoPRO user; a NULL
-- value means "not linked". The column is sparse (most users will
-- not link Telegram) so the unique index is partial-ish via the
-- nullability — Postgres + H2 both allow many NULLs in a UNIQUE column.

ALTER TABLE app_users ADD COLUMN telegram_id BIGINT;

ALTER TABLE app_users ADD CONSTRAINT app_users_telegram_id_unique UNIQUE (telegram_id);
