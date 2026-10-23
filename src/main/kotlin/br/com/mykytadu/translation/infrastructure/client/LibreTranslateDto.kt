package br.com.mykytadu.translation.infrastructure.client

import com.fasterxml.jackson.annotation.JsonProperty

internal data class LibreTranslateRequest(
    val q: String,
    val source: String,
    val target: String,
    val format: String,
    @JsonProperty("api_key")
    val apiKey: String? = null,
)

internal data class LibreTranslateResponse(
    val translatedText: String,
    val detectedLanguage: DetectedLanguage? = null,
) {
    internal data class DetectedLanguage(val confidence: Double? = null, val language: String? = null)
}
