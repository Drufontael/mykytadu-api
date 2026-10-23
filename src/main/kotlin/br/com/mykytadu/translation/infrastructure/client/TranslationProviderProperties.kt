package br.com.mykytadu.translation.infrastructure.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mykytadu.translation.provider")
data class TranslationProviderProperties(
    val url: String = "http://localhost:5000",
    val apiKey: String? = null,
    val connectTimeoutSeconds: Long = 2,
    val readTimeoutSeconds: Long = 5,
    val providerName: String = "libretranslate",
    val modelVersion: String = "argos-1.9",
    val mode: String = "libretranslate",
) {
    override fun toString(): String =
        "TranslationProviderProperties(url=$url, apiKey=${if (apiKey.isNullOrBlank()) "none" else "***"}, " +
            "connectTimeout=${connectTimeoutSeconds}s, readTimeout=${readTimeoutSeconds}s, " +
            "provider=$providerName, model=$modelVersion, mode=$mode)"
}
