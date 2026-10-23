package br.com.mykytadu.translation.infrastructure.observability

import br.com.mykytadu.translation.application.port.TranslationTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

internal class MicrometerTranslationTelemetry(private val registry: MeterRegistry) : TranslationTelemetry {

    private val cacheHitCounter: Counter = registry.counter("mykytadu.translation.cache", "result", "hit")
    private val cacheMissCounter: Counter = registry.counter("mykytadu.translation.cache", "result", "miss")
    private val charactersCounter: Counter = registry.counter(
        "mykytadu.translation.characters.translated",
        "module",
        "translation",
    )

    override fun recordCacheHit() {
        cacheHitCounter.increment()
    }

    override fun recordCacheMiss() {
        cacheMissCounter.increment()
    }

    override fun recordCharactersTranslated(charactersCount: Int) {
        if (charactersCount > 0) {
            charactersCounter.increment(charactersCount.toDouble())
        }
    }

    override fun recordProviderCall(duration: Duration, success: Boolean, provider: String) {
        Timer.builder("mykytadu.translation.provider.latency")
            .description("Latency of calls to downstream translation provider")
            .tag("provider", provider)
            .tag("success", success.toString())
            .register(registry)
            .record(duration)
    }
}
