package br.com.mykytadu.translation.application.port

import br.com.mykytadu.translation.domain.model.CachedTranslation
import br.com.mykytadu.translation.domain.model.Language
import java.time.Instant

interface TranslationCache {

    fun findValidTranslation(contentHash: String, targetLanguage: Language, now: Instant): CachedTranslation?

    fun save(translation: CachedTranslation): CachedTranslation

    fun deleteExpiredBefore(now: Instant): Int
}
