package br.com.mykytadu.translation.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class MicrometerTranslationTelemetryTest {

    private val registry = SimpleMeterRegistry()
    private val telemetry = MicrometerTranslationTelemetry(registry)

    @Test
    fun `records cache hit and miss counters`() {
        telemetry.recordCacheHit()
        telemetry.recordCacheHit()
        telemetry.recordCacheMiss()

        val hitCounter = registry.find("mykytadu.translation.cache")
            .tag("result", "hit")
            .counter()
        val missCounter = registry.find("mykytadu.translation.cache")
            .tag("result", "miss")
            .counter()

        assertThat(hitCounter).isNotNull
        assertThat(hitCounter!!.count()).isEqualTo(2.0)

        assertThat(missCounter).isNotNull
        assertThat(missCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `records characters translated counter`() {
        telemetry.recordCharactersTranslated(450)
        telemetry.recordCharactersTranslated(50)

        val counter = registry.find("mykytadu.translation.characters.translated")
            .tag("module", "translation")
            .counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(500.0)
    }

    @Test
    fun `records provider call latency and status`() {
        telemetry.recordProviderCall(Duration.ofMillis(350), success = true, provider = "libretranslate")
        telemetry.recordProviderCall(Duration.ofMillis(1200), success = false, provider = "libretranslate")

        val successTimer = registry.find("mykytadu.translation.provider.latency")
            .tag("provider", "libretranslate")
            .tag("success", "true")
            .timer()

        val failureTimer = registry.find("mykytadu.translation.provider.latency")
            .tag("provider", "libretranslate")
            .tag("success", "false")
            .timer()

        assertThat(successTimer).isNotNull
        assertThat(successTimer!!.count()).isEqualTo(1L)

        assertThat(failureTimer).isNotNull
        assertThat(failureTimer!!.count()).isEqualTo(1L)
    }
}
