-- Subscription tier per account. New self-service signups start on TRIAL;
-- accounts that existed before plans are grandfathered to the top tier so
-- introducing limits never locks an existing customer out.
ALTER TABLE accounts ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'TRIAL';
UPDATE accounts SET plan = 'PRO';
