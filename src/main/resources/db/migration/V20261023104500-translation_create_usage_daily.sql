CREATE TABLE translation.usage_daily (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    characters_count BIGINT NOT NULL DEFAULT 0,
    requests_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT usage_daily_pk PRIMARY KEY (id),
    CONSTRAINT usage_daily_characters_non_negative_ck CHECK (characters_count >= 0),
    CONSTRAINT usage_daily_requests_non_negative_ck CHECK (requests_count >= 0)
);

CREATE UNIQUE INDEX usage_daily_user_date_uk
    ON translation.usage_daily (user_id, usage_date);

CREATE INDEX usage_daily_date_idx
    ON translation.usage_daily (usage_date);
