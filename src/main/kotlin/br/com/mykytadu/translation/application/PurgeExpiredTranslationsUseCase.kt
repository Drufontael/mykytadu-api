package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.application.port.TranslationCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class PurgeExpiredTranslationsUseCase(private val translationCache: TranslationCache, private val clock: Clock) {

    fun purge(before: Instant = clock.instant()): Int {
        val deletedCount = translationCache.deleteExpiredBefore(before)
        if (deletedCount > 0) {
            logger.info("Purged {} expired translation cache entries before {}", deletedCount, before)
        } else {
            logger.debug("No expired translation cache entries to purge before {}", before)
        }
        return deletedCount
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PurgeExpiredTranslationsUseCase::class.java)
    }
}
