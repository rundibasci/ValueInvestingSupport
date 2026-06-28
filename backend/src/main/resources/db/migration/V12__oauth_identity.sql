-- OAuth identity table: links external identity providers to platform users
CREATE TABLE oauth_identity (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider         VARCHAR(30)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email   VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_oauth_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_oauth_identity_user ON oauth_identity(user_id);
CREATE INDEX idx_oauth_identity_email ON oauth_identity(provider, provider_email);

-- Allow NULL password_hash for OAuth-only users
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;
