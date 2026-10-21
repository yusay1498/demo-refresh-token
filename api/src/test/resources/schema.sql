CREATE TABLE IF NOT EXISTS refresh_token_family (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT chk_refresh_token_family_expiry
        CHECK (absolute_expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_family_username
    ON refresh_token_family (username);

CREATE INDEX IF NOT EXISTS idx_refresh_token_family_absolute_expires_at
    ON refresh_token_family (absolute_expires_at);

COMMENT ON TABLE refresh_token_family IS 'Refresh token family representing one login session';
COMMENT ON COLUMN refresh_token_family.id IS 'UUID identifying the login session';
COMMENT ON COLUMN refresh_token_family.username IS 'LDAP subject that owns the session';
COMMENT ON COLUMN refresh_token_family.created_at IS 'Time when the family was created';
COMMENT ON COLUMN refresh_token_family.absolute_expires_at IS 'Non-renewable absolute expiration time';
COMMENT ON COLUMN refresh_token_family.revoked_at IS 'Time when the entire family was revoked';

CREATE TABLE IF NOT EXISTS refresh_token (
    id VARCHAR(36) PRIMARY KEY,
    family_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_id VARCHAR(36),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_family
        FOREIGN KEY (family_id) REFERENCES refresh_token_family (id)
            ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_refresh_token_replacement
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id)
            ON DELETE SET NULL ON UPDATE RESTRICT
            DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_refresh_token_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_family_id
    ON refresh_token (family_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_replaced_by_id
    ON refresh_token (replaced_by_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_token_current_per_family
    ON refresh_token (family_id)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE refresh_token IS 'Hashed refresh token history for rotation and reuse detection';
COMMENT ON COLUMN refresh_token.id IS 'UUID identifying this refresh token record';
COMMENT ON COLUMN refresh_token.family_id IS 'Owning login session family';
COMMENT ON COLUMN refresh_token.token_hash IS 'SHA-256 hexadecimal digest of the opaque token';
COMMENT ON COLUMN refresh_token.issued_at IS 'Time when this token was issued';
COMMENT ON COLUMN refresh_token.expires_at IS 'Idle expiration time, capped by family absolute expiration';
COMMENT ON COLUMN refresh_token.consumed_at IS 'Time when rotation consumed this token';
COMMENT ON COLUMN refresh_token.revoked_at IS 'Time when this token record was revoked';
COMMENT ON COLUMN refresh_token.replaced_by_id IS 'Record ID of the token issued by rotation';
