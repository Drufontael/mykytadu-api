ALTER TABLE identity.sessions
    ADD COLUMN parent_session_id UUID,
    ADD COLUMN rotated_to_session_id UUID,
    ADD COLUMN rotation_idempotency_key_hash TEXT,
    ADD COLUMN refresh_derivation_kid TEXT,
    ADD COLUMN replay_until TIMESTAMPTZ,
    ADD COLUMN replay_access_token_id UUID,
    ADD COLUMN replay_access_issued_at TIMESTAMPTZ,
    ADD COLUMN replay_access_expires_at TIMESTAMPTZ;

ALTER TABLE identity.sessions
    ADD CONSTRAINT sessions_parent_fk FOREIGN KEY (parent_session_id)
        REFERENCES identity.sessions (id),
    ADD CONSTRAINT sessions_rotated_to_fk FOREIGN KEY (rotated_to_session_id)
        REFERENCES identity.sessions (id),
    ADD CONSTRAINT sessions_lineage_self_ck CHECK (
        parent_session_id IS NULL OR parent_session_id <> id
    ),
    ADD CONSTRAINT sessions_rotation_self_ck CHECK (
        rotated_to_session_id IS NULL OR rotated_to_session_id <> id
    ),
    ADD CONSTRAINT sessions_rotation_metadata_ck CHECK (
        (
            revoke_reason = 'rotated'
            AND rotated_to_session_id IS NOT NULL
            AND rotation_idempotency_key_hash IS NOT NULL
            AND char_length(rotation_idempotency_key_hash) = 64
            AND refresh_derivation_kid IS NOT NULL
            AND char_length(refresh_derivation_kid) BETWEEN 1 AND 100
            AND replay_until IS NOT NULL
            AND replay_until > revoked_at
            AND replay_access_token_id IS NOT NULL
            AND replay_access_issued_at IS NOT NULL
            AND replay_access_expires_at IS NOT NULL
            AND replay_access_expires_at > replay_access_issued_at
        )
        OR (
            revoke_reason IS DISTINCT FROM 'rotated'
            AND rotated_to_session_id IS NULL
            AND rotation_idempotency_key_hash IS NULL
            AND refresh_derivation_kid IS NULL
            AND replay_until IS NULL
            AND replay_access_token_id IS NULL
            AND replay_access_issued_at IS NULL
            AND replay_access_expires_at IS NULL
        )
    );

CREATE UNIQUE INDEX sessions_parent_session_uk
    ON identity.sessions (parent_session_id)
    WHERE parent_session_id IS NOT NULL;

CREATE INDEX sessions_rotated_to_idx
    ON identity.sessions (rotated_to_session_id)
    WHERE rotated_to_session_id IS NOT NULL;
