package br.com.mykytadu.translation.infrastructure.persistence

import br.com.mykytadu.translation.application.port.TranslationCache
import br.com.mykytadu.translation.domain.model.CachedTranslation
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
internal class JpaTranslationCache(private val repository: SpringDataTranslationRepository) : TranslationCache {

    @Transactional(readOnly = true)
    override fun findValidTranslation(contentHash: String, targetLanguage: Language, now: Instant): CachedTranslation? {
        val entity = repository.findValidTranslation(contentHash, targetLanguage.code, now)
        return entity?.toDomain()
    }

    @Transactional
    override fun save(translation: CachedTranslation): CachedTranslation {
        val inserted = repository.insertIfAbsent(
            id = translation.id,
            contentHash = translation.contentHash,
            sourceLanguage = translation.sourceLanguage.code,
            targetLanguage = translation.targetLanguage.code,
            contentType = translation.contentType.value,
            translation = translation.translation,
            provider = translation.provider,
            modelVersion = translation.modelVersion,
            createdAt = translation.createdAt,
            expiresAt = translation.expiresAt,
        )
        if (inserted > 0) {
            return translation
        }
        logger.warn("Concurrent translation cache insert for hash={}; retrieving existing", translation.contentHash)
        val existing = repository.findValidTranslation(
            contentHash = translation.contentHash,
            targetLanguage = translation.targetLanguage.code,
            now = translation.createdAt,
        )
        return existing?.toDomain() ?: translation
    }

    @Transactional
    override fun deleteExpiredBefore(now: Instant): Int = repository.deleteExpiredBefore(now)

    private fun TranslationEntity.toDomain(): CachedTranslation = CachedTranslation(
        id = id,
        contentHash = contentHash,
        sourceLanguage = Language.fromCode(sourceLanguage),
        targetLanguage = Language.fromCode(targetLanguage),
        contentType = ContentType.fromValue(contentType),
        translation = translation,
        provider = provider,
        modelVersion = modelVersion,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    private fun CachedTranslation.toEntity(): TranslationEntity = TranslationEntity(
        id = id,
        contentHash = contentHash,
        sourceLanguage = sourceLanguage.code,
        targetLanguage = targetLanguage.code,
        contentType = contentType.value,
        translation = translation,
        provider = provider,
        modelVersion = modelVersion,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(JpaTranslationCache::class.java)
    }
}
