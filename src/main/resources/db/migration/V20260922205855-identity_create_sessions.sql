CREATE TABLE identity.sessions (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    refresh_token_hash TEXT NOT NULL,
    token_family_id UUID NOT NULL,
    client_id TEXT,
    csrf_token_hash TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT sessions_pk PRIMARY KEY (id),
    CONSTRAINT sessions_user_fk FOREIGN KEY (user_id)
        REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT sessions_refresh_hash_length_ck CHECK (char_length(refresh_token_hash) = 64),
    CONSTRAINT sessions_client_id_ck CHECK (
        client_id IS NULL OR client_id IN (
            'mykytadu-web',
            'mykytadu-android',
            'mykytadu-ios',
            'mykytadu-desktop'
        )
    ),
    CONSTRAINT sessions_csrf_hash_ck CHECK (
        (client_id = 'mykytadu-web' AND csrf_token_hash IS NOT NULL AND char_length(csrf_token_hash) = 64)
        OR (client_id IS DISTINCT FROM 'mykytadu-web' AND csrf_token_hash IS NULL)
    ),
    CONSTRAINT sessions_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT sessions_revocation_ck CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_at >= created_at AND revoke_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX sessions_refresh_token_hash_uk
    ON identity.sessions (refresh_token_hash);

CREATE INDEX sessions_active_user_idx
    ON identity.sessions (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX sessions_token_family_idx
    ON identity.sessions (token_family_id);
