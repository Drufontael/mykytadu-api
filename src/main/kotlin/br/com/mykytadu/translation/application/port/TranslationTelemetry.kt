package br.com.mykytadu.translation.application.port

import java.time.Duration

interface TranslationTelemetry {
    fun recordCacheHit()
    fun recordCacheMiss()
    fun recordCharactersTranslated(charactersCount: Int)
    fun recordProviderCall(duration: Duration, success: Boolean, provider: String)
}
