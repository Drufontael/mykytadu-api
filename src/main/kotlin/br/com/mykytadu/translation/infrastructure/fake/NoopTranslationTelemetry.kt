package br.com.mykytadu.translation.infrastructure.fake

import br.com.mykytadu.translation.application.port.TranslationTelemetry
import java.time.Duration

class NoopTranslationTelemetry : TranslationTelemetry {
    override fun recordCacheHit() = Unit
    override fun recordCacheMiss() = Unit
    override fun recordCharactersTranslated(charactersCount: Int) = Unit
    override fun recordProviderCall(duration: Duration, success: Boolean, provider: String) = Unit
}
