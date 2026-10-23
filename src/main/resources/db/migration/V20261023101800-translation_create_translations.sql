CREATE TABLE translation.translations (
    id UUID NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    source_language TEXT NOT NULL,
    target_language TEXT NOT NULL,
    content_type TEXT NOT NULL,
    translation TEXT NOT NULL,
    provider TEXT NOT NULL,
    model_version TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT translations_pk PRIMARY KEY (id),
    CONSTRAINT translations_content_hash_length_ck CHECK (char_length(content_hash) = 64),
    CONSTRAINT translations_source_language_ck CHECK (char_length(source_language) BETWEEN 2 AND 16),
    CONSTRAINT translations_target_language_ck CHECK (char_length(target_language) BETWEEN 2 AND 16),
    CONSTRAINT translations_content_type_ck CHECK (char_length(content_type) BETWEEN 1 AND 64),
    CONSTRAINT translations_translation_not_empty_ck CHECK (char_length(translation) > 0),
    CONSTRAINT translations_provider_not_empty_ck CHECK (char_length(provider) > 0),
    CONSTRAINT translations_expires_after_created_ck CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX translations_content_hash_target_uk
    ON translation.translations (content_hash, target_language);

CREATE INDEX translations_expires_at_idx
    ON translation.translations (expires_at);
