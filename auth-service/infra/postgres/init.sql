-- Runs ONCE on first PostgreSQL container creation.
-- Flyway handles all table creation. This only ensures DB prerequisites exist.

-- The POSTGRES_USER and POSTGRES_PASSWORD env vars are already used by the
-- official postgres image to create the superuser and default database.
-- We just need to ensure auth_user has the right grants on auth_db.

-- Grant all privileges on auth_db to auth_user
-- (postgres image already created both from env vars)
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user;

-- Grant schema-level privileges so auth_user can create tables via Flyway
\c auth_db
GRANT ALL ON SCHEMA public TO auth_user;
GRANT CREATE ON SCHEMA public TO auth_user;