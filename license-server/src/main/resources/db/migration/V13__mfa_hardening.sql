-- =====================================================================
--  V13: MFA hardening — TOTP backup/recovery codes + per-account require-MFA.
--
--  • app_users.totp_backup_codes: newline-joined SHA-256 hashes of one-time
--    recovery codes, handed to the user (in plaintext, once) when they enable
--    2FA. A code is consumed (removed) on use, so a lost authenticator app no
--    longer means an admin reset.
--  • accounts.require_mfa: when true, the account's users are nudged to set up
--    2FA (surfaced as mfaSetupRequired in /me). Soft by design — it never hard-
--    blocks login, so turning it on can't lock anyone out.
-- =====================================================================

ALTER TABLE app_users ADD COLUMN totp_backup_codes VARCHAR(1000);
ALTER TABLE accounts  ADD COLUMN require_mfa BOOLEAN NOT NULL DEFAULT FALSE;
