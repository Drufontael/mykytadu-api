CREATE TABLE identity.users (
    id UUID NOT NULL,
    email TEXT NOT NULL,
    normalized_email TEXT NOT NULL,
    display_name TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_email_length_ck CHECK (char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT users_normalized_email_length_ck CHECK (char_length(normalized_email) BETWEEN 3 AND 254),
    CONSTRAINT users_display_name_length_ck CHECK (
        display_name IS NULL OR char_length(display_name) BETWEEN 1 AND 80
    ),
    CONSTRAINT users_status_ck CHECK (status IN ('pending', 'active', 'blocked', 'deleted'))
);

CREATE UNIQUE INDEX users_normalized_email_uk
    ON identity.users (normalized_email);

CREATE TABLE identity.password_credentials (
    user_id UUID NOT NULL,
    password_hash TEXT NOT NULL,
    algorithm TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT password_credentials_pk PRIMARY KEY (user_id),
    CONSTRAINT password_credentials_user_fk FOREIGN KEY (user_id)
        REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT password_credentials_algorithm_ck CHECK (algorithm = 'argon2id'),
    CONSTRAINT password_credentials_hash_not_blank_ck CHECK (char_length(password_hash) > 0)
);

CREATE TABLE identity.roles (
    user_id UUID NOT NULL,
    role TEXT NOT NULL,
    CONSTRAINT roles_pk PRIMARY KEY (user_id, role),
    CONSTRAINT roles_user_fk FOREIGN KEY (user_id)
        REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT roles_role_ck CHECK (role IN ('USER', 'ADMIN'))
);
