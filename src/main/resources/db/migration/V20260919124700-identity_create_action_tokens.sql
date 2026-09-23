CREATE TABLE identity.action_tokens (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    type TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT action_tokens_pk PRIMARY KEY (id),
    CONSTRAINT action_tokens_user_fk FOREIGN KEY (user_id)
        REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT action_tokens_type_ck CHECK (type IN ('email_verification', 'password_reset')),
    CONSTRAINT action_tokens_hash_length_ck CHECK (char_length(token_hash) = 64),
    CONSTRAINT action_tokens_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT action_tokens_consumed_at_ck CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    )
);

CREATE UNIQUE INDEX action_tokens_token_hash_uk
    ON identity.action_tokens (token_hash);

CREATE INDEX action_tokens_active_user_type_idx
    ON identity.action_tokens (user_id, type)
    WHERE consumed_at IS NULL;

CREATE INDEX action_tokens_expires_at_idx
    ON identity.action_tokens (expires_at);
