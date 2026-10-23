package br.com.mykytadu.translation.domain.model

import java.time.Instant
import java.util.UUID

data class CachedTranslation(
    val id: UUID,
    val contentHash: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val contentType: ContentType,
    val translation: String,
    val provider: String,
    val modelVersion: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString(): String =
        "CachedTranslation(id=$id, contentHash=$contentHash, source=$sourceLanguage, target=$targetLanguage, " +
            "type=$contentType, provider=$provider, modelVersion=$modelVersion, " +
            "translationLength=${translation.length}, createdAt=$createdAt, expiresAt=$expiresAt)"
}
