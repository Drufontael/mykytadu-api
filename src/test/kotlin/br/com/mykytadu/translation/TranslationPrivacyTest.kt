package br.com.mykytadu.translation

import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.ProviderTranslationResult
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.model.NormalizedText
import br.com.mykytadu.translation.domain.model.TranslationFormat
import br.com.mykytadu.translation.domain.model.TranslationResult
import br.com.mykytadu.translation.infrastructure.client.TranslationProviderProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TranslationPrivacyTest {

    @Test
    fun `properties toString masks secret api key`() {
        val properties = TranslationProviderProperties(
            url = "http://localhost:5000",
            apiKey = "super-secret-api-key-12345",
        )

        val str = properties.toString()

        assertThat(str)
            .doesNotContain("super-secret-api-key-12345")
            .contains("apiKey=***")
    }

    @Test
    fun `normalized text toString masks sensitive content`() {
        val sensitive = "Sensitive original description text with private spoilers"
        val normalized = NormalizedText.of(sensitive)

        val str = normalized.toString()

        assertThat(str)
            .doesNotContain(sensitive)
            .contains("length=${sensitive.length}")
    }

    @Test
    fun `command and result toString mask full texts`() {
        val rawInput = "Top secret anime plot synopsis"
        val translated = "Sinopse ultrassecreta do enredo"

        val command = ProviderTranslationCommand(
            text = rawInput,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            format = TranslationFormat.TEXT,
        )

        val providerResult = ProviderTranslationResult(
            translatedText = translated,
            detectedSourceLanguage = Language.EN,
            provider = "libretranslate",
        )

        val domainResult = TranslationResult(
            translation = translated,
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            cached = false,
        )

        assertThat(command.toString())
            .doesNotContain(rawInput)
            .contains("textLength=${rawInput.length}")

        assertThat(providerResult.toString())
            .doesNotContain(translated)
            .contains("length=${translated.length}")

        assertThat(domainResult.toString())
            .doesNotContain(translated)
            .contains("length=${translated.length}")
    }

    @Test
    fun `cached translation toString masks full text`() {
        val translated = "Texto traduzido em cache"
        val cached = br.com.mykytadu.translation.domain.model.CachedTranslation(
            id = java.util.UUID.randomUUID(),
            contentHash = "a".repeat(64),
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            translation = translated,
            provider = "libretranslate",
            modelVersion = "1.0",
            createdAt = java.time.Instant.now(),
            expiresAt = java.time.Instant.now().plus(java.time.Duration.ofDays(30)),
        )

        assertThat(cached.toString())
            .doesNotContain(translated)
            .contains("translationLength=${translated.length}")
    }
}
