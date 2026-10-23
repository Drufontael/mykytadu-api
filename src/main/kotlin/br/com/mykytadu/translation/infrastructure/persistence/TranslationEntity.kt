package br.com.mykytadu.translation.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "translations", schema = "translation")
class TranslationEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "content_hash", nullable = false, length = 64)
    val contentHash: String,

    @Column(name = "source_language", nullable = false)
    val sourceLanguage: String,

    @Column(name = "target_language", nullable = false)
    val targetLanguage: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "translation", nullable = false)
    val translation: String,

    @Column(name = "provider", nullable = false)
    val provider: String,

    @Column(name = "model_version")
    val modelVersion: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
)
