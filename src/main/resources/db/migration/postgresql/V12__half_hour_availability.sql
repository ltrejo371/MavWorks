-- Only keep half hours that were fully available. Never expand a user's availability.
CREATE TABLE availability_half_hour_conversion AS
SELECT user_id, available_date, CAST(FLOOR(slot / 2.0) AS INTEGER) AS slot
FROM availability
GROUP BY user_id, available_date, CAST(FLOOR(slot / 2.0) AS INTEGER)
HAVING COUNT(*) = 2;
DELETE FROM availability;
INSERT INTO availability (user_id, available_date, slot)
SELECT user_id, available_date, slot FROM availability_half_hour_conversion;
DROP TABLE availability_half_hour_conversion;
ALTER TABLE availability ADD CONSTRAINT availability_half_hour_slot CHECK (slot BETWEEN 0 AND 47);
