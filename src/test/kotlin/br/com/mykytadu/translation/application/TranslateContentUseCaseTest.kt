package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.application.port.TranslationIdGenerator
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.exception.TranslationProviderUnavailableException
import br.com.mykytadu.translation.infrastructure.fake.FakeTranslationProvider
import br.com.mykytadu.translation.infrastructure.fake.InMemoryTranslationCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class TranslateContentUseCaseTest {

    private val fakeProvider = FakeTranslationProvider()
    private val cache = InMemoryTranslationCache()
    private val fixedId = UUID.fromString("018d45f3-524c-7000-8000-000000000001")
    private val idGenerator = TranslationIdGenerator { fixedId }
    private var currentInstant: Instant = Instant.parse("2026-10-23T10:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = currentInstant
    }
    private val useCase = TranslateContentUseCase(
        provider = fakeProvider,
        cache = cache,
        idGenerator = idGenerator,
        clock = clock,
    )

    @BeforeEach
    fun reset() {
        fakeProvider.clear()
        cache.clear()
        currentInstant = Instant.parse("2026-10-23T10:00:00Z")
    }

    @Test
    fun `translates content on cache miss, caches it, and returns cached=false`() {
        fakeProvider.addCannedResponse("Attack on Titan description", "Descrição de Shingeki no Kyojin")

        val command = TranslateContentCommand(
            text = "  Attack on Titan description\r\n",
            sourceLanguage = "en",
            targetLanguage = "pt-BR",
            contentType = "ANIME_DESCRIPTION",
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
        val success = outcome as TranslationOutcome.Success
        assertThat(success.response.translation).isEqualTo("Descrição de Shingeki no Kyojin")
        assertThat(success.response.sourceLanguage).isEqualTo("en")
        assertThat(success.response.targetLanguage).isEqualTo("pt-BR")
        assertThat(success.response.cached).isFalse()

        // Second call with same parameters hits the cache
        fakeProvider.clear()
        fakeProvider.errorToThrow = TranslationProviderUnavailableException("Should not be called on cache hit")

        val cachedOutcome = useCase.translate(command)
        assertThat(cachedOutcome).isInstanceOf(TranslationOutcome.Success::class.java)
        val cachedSuccess = cachedOutcome as TranslationOutcome.Success
        assertThat(cachedSuccess.response.translation).isEqualTo("Descrição de Shingeki no Kyojin")
        assertThat(cachedSuccess.response.cached).isTrue()
    }

    @Test
    fun `does not use expired cache entry and fetches from provider`() {
        fakeProvider.addCannedResponse("Initial text", "Tradução inicial")

        val command = TranslateContentCommand(
            text = "Initial text",
            sourceLanguage = "en",
            targetLanguage = "pt-BR",
            contentType = "ANIME_DESCRIPTION",
        )

        val firstOutcome = useCase.translate(command) as TranslationOutcome.Success
        assertThat(firstOutcome.response.cached).isFalse()

        // Advance clock past 30 days
        currentInstant = currentInstant.plus(Duration.ofDays(31))
        fakeProvider.addCannedResponse("Initial text", "Tradução atualizada")

        val expiredOutcome = useCase.translate(command) as TranslationOutcome.Success
        assertThat(expiredOutcome.response.translation).isEqualTo("Tradução atualizada")
        assertThat(expiredOutcome.response.cached).isFalse()
    }

    @Test
    fun `returns InvalidContent when text contains malicious script`() {
        val command = TranslateContentCommand(
            text = "Malicious <script>alert('xss')</script>",
            sourceLanguage = "en",
            targetLanguage = "pt-BR",
            contentType = "ANIME_DESCRIPTION",
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.InvalidContent::class.java)
    }

    @Test
    fun `returns Timeout when provider times out on cache miss`() {
        fakeProvider.errorToThrow = TranslationProviderTimeoutException("Simulated timeout")

        val command = TranslateContentCommand(
            text = "Valid text",
            sourceLanguage = "en",
            targetLanguage = "pt-BR",
            contentType = "ANIME_DESCRIPTION",
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isEqualTo(TranslationOutcome.Timeout)
    }

    @Test
    fun `returns ProviderFailed when provider is unavailable on cache miss`() {
        fakeProvider.errorToThrow = TranslationProviderUnavailableException("Connection refused")

        val command = TranslateContentCommand(
            text = "Valid text",
            sourceLanguage = "en",
            targetLanguage = "pt-BR",
            contentType = "ANIME_DESCRIPTION",
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.ProviderFailed::class.java)
    }
}
