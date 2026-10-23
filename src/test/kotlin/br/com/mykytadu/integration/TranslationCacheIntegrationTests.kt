package br.com.mykytadu.integration

import br.com.mykytadu.translation.application.PurgeExpiredTranslationsUseCase
import br.com.mykytadu.translation.application.port.TranslationCache
import br.com.mykytadu.translation.domain.model.CachedTranslation
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class TranslationCacheIntegrationTests(
    @Autowired private val cache: TranslationCache,
    @Autowired private val purgeUseCase: PurgeExpiredTranslationsUseCase,
) {

    @Test
    fun `persists translation, retrieves valid hit, and ignores expired entries`() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val contentHash = "b".repeat(64)
        val validId = UUID.fromString("018d45f3-524c-7000-8000-000000000010")

        val validEntry = CachedTranslation(
            id = validId,
            contentHash = contentHash,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            translation = "Tradução válida em cache",
            provider = "libretranslate",
            modelVersion = "argos-1.9",
            createdAt = now,
            expiresAt = now.plus(Duration.ofDays(30)),
        )

        cache.save(validEntry)

        val retrieved = cache.findValidTranslation(contentHash, Language.PT_BR, now)
        assertThat(retrieved).isNotNull
        assertThat(retrieved!!.translation).isEqualTo("Tradução válida em cache")
        assertThat(retrieved.provider).isEqualTo("libretranslate")
        assertThat(retrieved.expiresAt).isEqualTo(now.plus(Duration.ofDays(30)))

        // Querying at a future time past expires_at returns null
        val future = now.plus(Duration.ofDays(31))
        val expiredResult = cache.findValidTranslation(contentHash, Language.PT_BR, future)
        assertThat(expiredResult).isNull()
    }

    @Test
    fun `purges expired entries idempotently`() {
        val baseTime = Instant.parse("2026-10-01T00:00:00Z")
        val contentHash = "c".repeat(64)
        val id = UUID.fromString("018d45f3-524c-7000-8000-000000000020")

        val oldEntry = CachedTranslation(
            id = id,
            contentHash = contentHash,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            translation = "Tradução antiga",
            provider = "libretranslate",
            modelVersion = null,
            createdAt = baseTime,
            expiresAt = baseTime.plus(Duration.ofDays(30)), // expired on Oct 31
        )

        cache.save(oldEntry)

        val purgeTime = baseTime.plus(Duration.ofDays(31))
        val deletedCount = purgeUseCase.purge(purgeTime)
        assertThat(deletedCount).isGreaterThanOrEqualTo(1)

        val afterPurge = cache.findValidTranslation(contentHash, Language.PT_BR, baseTime)
        assertThat(afterPurge).isNull()
    }

    @Test
    fun `handles concurrent duplicate insert gracefully without throwing error`() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val contentHash = "d".repeat(64)
        val id1 = UUID.fromString("018d45f3-524c-7000-8000-000000000030")
        val id2 = UUID.fromString("018d45f3-524c-7000-8000-000000000031")

        val entry1 = CachedTranslation(
            id = id1,
            contentHash = contentHash,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            translation = "Primeira tradução inserida",
            provider = "libretranslate",
            modelVersion = null,
            createdAt = now,
            expiresAt = now.plus(Duration.ofDays(30)),
        )

        val entry2 = CachedTranslation(
            id = id2,
            contentHash = contentHash,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            translation = "Segunda tradução concorrente",
            provider = "libretranslate",
            modelVersion = null,
            createdAt = now,
            expiresAt = now.plus(Duration.ofDays(30)),
        )

        val saved1 = cache.save(entry1)
        val saved2 = cache.save(entry2)

        assertThat(saved1).isNotNull
        assertThat(saved2).isNotNull
        assertThat(saved2.translation).isEqualTo("Primeira tradução inserida")
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSqlIntegrationFixture.newContainer()
    }
}
