package br.com.mykytadu.translation.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

internal interface SpringDataTranslationRepository : JpaRepository<TranslationEntity, UUID> {

    @Query(
        """
        SELECT t FROM TranslationEntity t
        WHERE t.contentHash = :contentHash
          AND t.targetLanguage = :targetLanguage
          AND t.expiresAt > :now
        ORDER BY t.createdAt DESC
        LIMIT 1
        """,
    )
    fun findValidTranslation(
        @Param("contentHash") contentHash: String,
        @Param("targetLanguage") targetLanguage: String,
        @Param("now") now: Instant,
    ): TranslationEntity?

    @Modifying(clearAutomatically = true)
    @Query(
        """
        DELETE FROM TranslationEntity t
        WHERE t.expiresAt <= :now
        """,
    )
    fun deleteExpiredBefore(@Param("now") now: Instant): Int

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO translation.translations (
                id, content_hash, source_language, target_language, content_type,
                translation, provider, model_version, created_at, expires_at
            ) VALUES (
                :id, :contentHash, :sourceLanguage, :targetLanguage, :contentType,
                :translation, :provider, :modelVersion, :createdAt, :expiresAt
            ) ON CONFLICT (content_hash, target_language) DO NOTHING
        """,
    )
    @Suppress("LongParameterList")
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("contentHash") contentHash: String,
        @Param("sourceLanguage") sourceLanguage: String,
        @Param("targetLanguage") targetLanguage: String,
        @Param("contentType") contentType: String,
        @Param("translation") translation: String,
        @Param("provider") provider: String,
        @Param("modelVersion") modelVersion: String?,
        @Param("createdAt") createdAt: Instant,
        @Param("expiresAt") expiresAt: Instant,
    ): Int
}
