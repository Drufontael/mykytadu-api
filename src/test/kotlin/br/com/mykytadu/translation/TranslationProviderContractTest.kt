package br.com.mykytadu.translation

import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.model.TranslationFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class TranslationProviderContractTest {

    abstract fun createProvider(): TranslationProvider

    @Test
    fun `translates explicit language text successfully`() {
        val provider = createProvider()
        val command = ProviderTranslationCommand(
            text = "Hello world",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            format = TranslationFormat.TEXT,
        )

        val result = provider.translate(command)

        assertThat(result.translatedText).isNotBlank()
        assertThat(result.provider).isNotBlank()
        assertThat(result.detectedSourceLanguage).isEqualTo(Language.EN)
    }

    @Test
    fun `handles auto language detection`() {
        val provider = createProvider()
        val command = ProviderTranslationCommand(
            text = "Hello world",
            sourceLanguage = Language.AUTO,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            format = TranslationFormat.TEXT,
        )

        val result = provider.translate(command)

        assertThat(result.translatedText).isNotBlank()
        assertThat(result.detectedSourceLanguage).isIn(Language.EN, Language.JA)
    }
}
