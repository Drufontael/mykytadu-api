package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.application.port.TranslationCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PurgeExpiredTranslationsUseCaseTest {

    private val fixedInstant = Instant.parse("2026-10-23T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val translationCache = mockk<TranslationCache>()
    private val useCase = PurgeExpiredTranslationsUseCase(translationCache, clock)

    @Test
    @DisplayName("purges expired translations using clock instant when no parameter is passed")
    fun purgesUsingClockInstant() {
        every { translationCache.deleteExpiredBefore(fixedInstant) } returns 42

        val deleted = useCase.purge()

        assertThat(deleted).isEqualTo(42)
        verify(exactly = 1) { translationCache.deleteExpiredBefore(fixedInstant) }
    }

    @Test
    @DisplayName("purges expired translations using explicit instant when provided")
    fun purgesUsingExplicitInstant() {
        val customInstant = Instant.parse("2026-10-20T00:00:00Z")
        every { translationCache.deleteExpiredBefore(customInstant) } returns 0

        val deleted = useCase.purge(customInstant)

        assertThat(deleted).isEqualTo(0)
        verify(exactly = 1) { translationCache.deleteExpiredBefore(customInstant) }
    }
}
