-- Runs once, on first Postgres container start (docker-entrypoint-initdb.d).
--
-- POSTGRES_DB already created the backend's database (default: savdopro).
-- The license server needs its OWN database: both apps define an `accounts`
-- and `app_users` table and keep their own Flyway history, so sharing a
-- single database would collide. Created here, owned by the same role.
CREATE DATABASE license;
