-- ====================================================================
--  V25: Customer birthday (tug'ilgan kun)
--
--  Optional birthday so the shop can greet the customer and offer a
--  birthday-month discount. Loyalty tiers (bronze/silver/gold) are
--  derived from points_total_earned, so they need no extra column.
-- ====================================================================
ALTER TABLE customers ADD COLUMN birthday DATE;
