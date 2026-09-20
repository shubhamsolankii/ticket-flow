-- V2: Create auth_credentials table
-- Depends on V1 (auth_users must exist for the FK reference).

CREATE TABLE auth_credentials (
                                  id                      VARCHAR(26)  NOT NULL,
                                  user_id                 VARCHAR(26)  NOT NULL,
                                  credential_type         VARCHAR(20)  NOT NULL,
                                  email                   VARCHAR(255),
                                  password_hash           VARCHAR(72),
                                  failed_login_attempts   INT          NOT NULL DEFAULT 0,
                                  locked_until            TIMESTAMPTZ,
                                  created_at              TIMESTAMPTZ  NOT NULL,
                                  updated_at              TIMESTAMPTZ  NOT NULL,

                                  CONSTRAINT pk_auth_credentials
                                      PRIMARY KEY (id),

                                  CONSTRAINT fk_auth_credentials_user
                                      FOREIGN KEY (user_id) REFERENCES auth_users(id)
                                          ON DELETE CASCADE,       -- if AuthUser is deleted, credentials go too

    -- One credential per login method per email.
    -- A user cannot register the same email twice with EMAIL_PASSWORD.
                                  CONSTRAINT uq_auth_credentials_email_type
                                      UNIQUE (email, credential_type)
);

-- Composite index: the exact lookup pattern used on every email login.
-- "Give me the EMAIL_PASSWORD credential for aryan@example.com"
CREATE INDEX idx_auth_credentials_email_type
    ON auth_credentials (email, credential_type);

-- Single-column index: "give me all credentials for user X"
-- Used when checking if a user already has a Google credential, for example.
CREATE INDEX idx_auth_credentials_user_id
    ON auth_credentials (user_id);