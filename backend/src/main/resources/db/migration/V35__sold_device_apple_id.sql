-- =====================================================================
--  V35: record the Apple ID (iCloud) signed into a sold iPhone.
--
--  iPhones can't be locked by IMEI from the app (Apple has no Knox Guard
--  equivalent). The de-facto method is the shop's own Apple ID + Find My
--  Lost Mode. We store ONLY the Apple ID (login/email) per sold device so the
--  shop knows which iCloud is on which phone — never the password.
-- =====================================================================

ALTER TABLE sold_devices ADD COLUMN apple_id VARCHAR(120);
