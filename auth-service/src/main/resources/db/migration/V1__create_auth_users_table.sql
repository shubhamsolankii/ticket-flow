-- V1: Create auth_users table
-- AuthUser is the root identity record. No credential fields live here.
-- Credentials (email+password, OAuth2, mobile) live in auth_credentials (V2).

CREATE TABLE auth_users (
                            id            VARCHAR(26)  NOT NULL,
                            email         VARCHAR(255) NOT NULL,
                            first_name    VARCHAR(100) NOT NULL,
                            last_name     VARCHAR(100) NOT NULL,
                            is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                            email_verified BOOLEAN     NOT NULL DEFAULT FALSE,
                            created_at    TIMESTAMPTZ  NOT NULL,
                            updated_at    TIMESTAMPTZ  NOT NULL,

                            CONSTRAINT pk_auth_users PRIMARY KEY (id),
                            CONSTRAINT uq_auth_users_email UNIQUE (email)
);

-- Index on email: every registration checks "does this email exist?"
-- Every login by email looks up this column. Must be sub-millisecond.
CREATE INDEX idx_auth_users_email ON auth_users (email);

-- Index on is_active: admin queries for active/inactive users.
-- Partial index — only index active=false rows (they're the minority).
-- Active users (the majority) are found via the email index anyway.
CREATE INDEX idx_auth_users_inactive ON auth_users (id) WHERE is_active = FALSE;