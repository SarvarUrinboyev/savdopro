-- =====================================================================
--  V36: device lifecycle — register IMEIs at INTAKE, not only at sale.
--
--  Every incoming IMEI-tracked unit becomes a sold_devices row with
--  status='IN_STOCK' and an intake_date. When it's sold, the same row flips to
--  status='SOLD' (linked to the sale + customer). This makes the table a full
--  device register: "did we sell this IMEI, when, and to whom?".
--
--  status values: IN_STOCK | SOLD | BLOCKED | RETURNED.
--  Existing rows were all sale-captured (old default 'ACTIVE') -> become SOLD.
-- =====================================================================

ALTER TABLE sold_devices ADD COLUMN intake_date TIMESTAMP;

UPDATE sold_devices SET status = 'SOLD' WHERE status = 'ACTIVE';
UPDATE sold_devices SET intake_date = created_at WHERE intake_date IS NULL;
