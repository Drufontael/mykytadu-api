package br.com.mykytadu.translation.infrastructure.fake

import br.com.mykytadu.translation.TranslationProviderContractTest
import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FakeTranslationProviderTest : TranslationProviderContractTest() {

    private val fakeProvider = FakeTranslationProvider()

    override fun createProvider(): TranslationProvider = fakeProvider

    @Test
    fun `returns canned response when configured`() {
        fakeProvider.addCannedResponse("Custom anime description", "Descrição personalizada de anime")

        val command = ProviderTranslationCommand(
            text = "Custom anime description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val result = fakeProvider.translate(command)

        assertThat(result.translatedText).isEqualTo("Descrição personalizada de anime")
    }

    @Test
    fun `throws configured exception on error simulation`() {
        fakeProvider.errorToThrow = TranslationProviderTimeoutException("Simulated timeout")

        val command = ProviderTranslationCommand(
            text = "Any text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThatThrownBy { fakeProvider.translate(command) }
            .isInstanceOf(TranslationProviderTimeoutException::class.java)
            .hasMessage("Simulated timeout")
    }
}
