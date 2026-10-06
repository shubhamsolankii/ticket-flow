-- =============================================================================
-- V1: Create auth schema and auth_users table
--
-- Flyway naming convention: V{version}__{description}.sql
--   V = versioned migration (runs once, in order)
--   {version} = monotonically increasing integer or timestamp
--   __ = double underscore separator (required by Flyway)
--   {description} = snake_case description
--
-- This migration runs ONCE on first startup. Flyway records it in
-- flyway_schema_history table with a checksum. If this file is modified
-- after running, Flyway startup fails (checksum mismatch). Never edit
-- a migration that has already been applied. Create a new V2__ instead.
-- =============================================================================

-- Create dedicated schema for auth-service.
-- All auth-service tables live in the 'auth' schema, not 'public'.
-- This namespaces auth-service's tables from other services if they share
-- the same PostgreSQL instance (local dev). In prod, auth-service gets
-- its own PostgreSQL instance entirely.
CREATE SCHEMA IF NOT EXISTS auth;

-- =============================================================================
-- auth_users table
-- Root identity record per user.
-- =============================================================================
CREATE TABLE auth.auth_users (
    -- ULID: 26-char lexicographically sortable string.
    -- VARCHAR(26) is exact — ULIDs are always 26 characters.
    -- NOT NULL + PRIMARY KEY creates the clustered index in PostgreSQL.
                                 id              VARCHAR(26)     NOT NULL,

    -- Email: unique per user, case-insensitive in practice.
    -- We store lowercase-normalized emails (enforced in AuthService).
    -- VARCHAR(255): RFC 5321 max email length is 254 characters.
                                 email           VARCHAR(255)    NOT NULL,

    -- UserStatus enum stored as string. CHECK constraint enforces
    -- only valid enum values can be inserted — DB-level validation.
                                 status          VARCHAR(30)     NOT NULL
                                     CONSTRAINT auth_users_status_check
                                         CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')),

    -- AuthProvider enum: which method this user originally registered with.
                                 auth_provider   VARCHAR(20)     NOT NULL
                                     CONSTRAINT auth_users_auth_provider_check
                                         CHECK (auth_provider IN ('EMAIL', 'MOBILE', 'GOOGLE', 'GITHUB')),

    -- TIMESTAMPTZ: timestamp WITH timezone. PostgreSQL stores in UTC internally.
    -- Always use TIMESTAMPTZ, never TIMESTAMP — the latter has no timezone
    -- information and can silently lose timezone data during DST transitions.
                                 created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                 updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Nullable: null = not soft-deleted, timestamp = deleted at this time.
                                 deleted_at      TIMESTAMPTZ,

                                 CONSTRAINT auth_users_pkey PRIMARY KEY (id)
);

-- =============================================================================
-- INDEXES on auth_users
-- =============================================================================

-- Email lookup index: used in every login flow.
-- 'CREATE UNIQUE INDEX' vs 'UNIQUE constraint' on column:
--   Both create a unique B-tree index. The UNIQUE INDEX form gives more
--   control — you can add WHERE clauses (partial index), set fill factor, etc.
--   We use it here to add a partial index excluding deleted accounts.
--
-- Partial unique index: email is unique ONLY among non-deleted users.
-- This allows email reuse after account deletion (GDPR requirement).
-- A hard-deleted user's email can be re-registered.
CREATE UNIQUE INDEX idx_auth_users_email
    ON auth.auth_users (email)
    WHERE deleted_at IS NULL;

-- Status index: used for admin queries ("show me all suspended accounts")
-- and for auth flow status checks. Not selective enough for a standalone
-- lookup (only 4 values), but useful in compound queries.
-- We use a partial index for ACTIVE status — this is the hot path.
-- "Is this user ACTIVE?" is checked on every login.
CREATE INDEX idx_auth_users_status
    ON auth.auth_users (status)
    WHERE deleted_at IS NULL;

-- updated_at index: enables incremental sync queries.
-- "Give me all users updated since 2025-01-01" — this index makes it fast.
-- Without it, a full table scan every time.
CREATE INDEX idx_auth_users_updated_at
    ON auth.auth_users (updated_at DESC);

-- =============================================================================
-- TRIGGER: auto-update updated_at on every row mutation
-- PostgreSQL does not have @UpdateTimestamp built in — we use a trigger.
-- Hibernate's @UpdateTimestamp also sets this, but the DB trigger is the
-- safety net: even a raw SQL UPDATE (from a migration or admin script)
-- will correctly update this timestamp.
-- =============================================================================
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auth_users_updated_at_trigger
    BEFORE UPDATE ON auth.auth_users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

-- =============================================================================
-- Comment: document the table for future engineers (and your future self).
-- These show up in psql \d+ output and in DB documentation tools.
-- =============================================================================
COMMENT ON TABLE auth.auth_users IS 'Root identity record for each user. Owned by auth-service. Does not contain credentials or profile data.';
COMMENT ON COLUMN auth.auth_users.id IS 'ULID — 26-char lexicographically sortable unique identifier. Generated in application layer.';
COMMENT ON COLUMN auth.auth_users.email IS 'Lowercase-normalized email. Unique among non-deleted users (partial unique index).';
COMMENT ON COLUMN auth.auth_users.deleted_at IS 'Soft delete timestamp. NULL = active. Non-null = logically deleted.';