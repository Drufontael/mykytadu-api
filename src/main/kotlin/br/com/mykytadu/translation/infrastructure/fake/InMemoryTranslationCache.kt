package br.com.mykytadu.translation.infrastructure.fake

import br.com.mykytadu.translation.application.port.TranslationCache
import br.com.mykytadu.translation.domain.model.CachedTranslation
import br.com.mykytadu.translation.domain.model.Language
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryTranslationCache : TranslationCache {

    private val entries = ConcurrentHashMap<Pair<String, String>, CachedTranslation>()

    override fun findValidTranslation(
        contentHash: String,
        targetLanguage: Language,
        now: Instant,
    ): CachedTranslation? {
        val entry = entries[contentHash to targetLanguage.code] ?: return null
        return if (entry.expiresAt.isAfter(now)) entry else null
    }

    override fun save(translation: CachedTranslation): CachedTranslation {
        entries[translation.contentHash to translation.targetLanguage.code] = translation
        return translation
    }

    override fun deleteExpiredBefore(now: Instant): Int {
        val toRemove = entries.filterValues { !it.expiresAt.isAfter(now) }.keys
        toRemove.forEach { entries.remove(it) }
        return toRemove.size
    }

    fun clear() {
        entries.clear()
    }
}
