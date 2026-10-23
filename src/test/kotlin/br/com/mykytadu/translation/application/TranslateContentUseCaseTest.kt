package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.exception.TranslationProviderUnavailableException
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.infrastructure.fake.FakeTranslationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranslateContentUseCaseTest {

    private val fakeProvider = FakeTranslationProvider()
    private val useCase = TranslateContentUseCase(fakeProvider)

    @BeforeEach
    fun reset() {
        fakeProvider.clear()
    }

    @Test
    fun `translates content successfully and returns Success outcome`() {
        fakeProvider.addCannedResponse("Attack on Titan description", "Descrição de Shingeki no Kyojin")

        val command = TranslateContentCommand(
            text = "  Attack on Titan description\r\n",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.Success::class.java)
        val success = outcome as TranslationOutcome.Success
        assertThat(success.result.translation).isEqualTo("Descrição de Shingeki no Kyojin")
        assertThat(success.result.sourceLanguage).isEqualTo(Language.EN)
        assertThat(success.result.targetLanguage).isEqualTo(Language.PT_BR)
        assertThat(success.result.cached).isFalse()
    }

    @Test
    fun `returns InvalidContent when text contains malicious script`() {
        val command = TranslateContentCommand(
            text = "Malicious <script>alert('xss')</script>",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.InvalidContent::class.java)
    }

    @Test
    fun `returns Timeout when provider times out`() {
        fakeProvider.errorToThrow = TranslationProviderTimeoutException("Simulated timeout")

        val command = TranslateContentCommand(
            text = "Valid text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isEqualTo(TranslationOutcome.Timeout)
    }

    @Test
    fun `returns ProviderFailed when provider is unavailable`() {
        fakeProvider.errorToThrow = TranslationProviderUnavailableException("Connection refused")

        val command = TranslateContentCommand(
            text = "Valid text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val outcome = useCase.translate(command)

        assertThat(outcome).isInstanceOf(TranslationOutcome.ProviderFailed::class.java)
    }
}
