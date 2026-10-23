package br.com.mykytadu.translation.infrastructure.client

import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.domain.exception.TranslationProviderAuthenticationException
import br.com.mykytadu.translation.domain.exception.TranslationProviderQuotaExceededException
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.exception.TranslationProviderUnavailableException
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.model.TranslationFormat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class LibreTranslateProviderAdapterTest {

    private val properties = TranslationProviderProperties(
        url = "http://localhost:5000",
        apiKey = "secret-key",
        providerName = "libretranslate",
        modelVersion = "argos-1.9",
    )

    private val restClientBuilder = RestClient.builder()
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val adapter = LibreTranslateProviderAdapter(
        restClient = restClientBuilder.baseUrl(properties.url).build(),
        properties = properties,
    )

    @Test
    fun `translates english to portuguese successfully`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.q").value("Hello world"))
            .andExpect(jsonPath("$.source").value("en"))
            .andExpect(jsonPath("$.target").value("pt"))
            .andExpect(jsonPath("$.format").value("text"))
            .andExpect(jsonPath("$.api_key").value("secret-key"))
            .andRespond(
                withSuccess(
                    """{"translatedText": "Olá mundo"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val command = ProviderTranslationCommand(
            text = "Hello world",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            format = TranslationFormat.TEXT,
        )

        val result = adapter.translate(command)

        assertThat(result.translatedText).isEqualTo("Olá mundo")
        assertThat(result.detectedSourceLanguage).isEqualTo(Language.EN)
        assertThat(result.provider).isEqualTo("libretranslate")
        assertThat(result.modelVersion).isEqualTo("argos-1.9")
        mockServer.verify()
    }

    @Test
    fun `detects source language when auto`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.source").value("auto"))
            .andRespond(
                withSuccess(
                    """{"translatedText": "Ataque dos Titãs", """ +
                        """"detectedLanguage": {"confidence": 99.0, "language": "ja"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val command = ProviderTranslationCommand(
            text = "進撃の巨人",
            sourceLanguage = Language.AUTO,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
            format = TranslationFormat.TEXT,
        )

        val result = adapter.translate(command)

        assertThat(result.translatedText).isEqualTo("Ataque dos Titãs")
        assertThat(result.detectedSourceLanguage).isEqualTo(Language.JA)
        mockServer.verify()
    }

    @Test
    fun `maps 504 status to TranslationProviderTimeoutException`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))

        val command = ProviderTranslationCommand(
            text = "Text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThatThrownBy { adapter.translate(command) }
            .isInstanceOf(TranslationProviderTimeoutException::class.java)
    }

    @Test
    fun `maps 500 status to TranslationProviderUnavailableException`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val command = ProviderTranslationCommand(
            text = "Text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThatThrownBy { adapter.translate(command) }
            .isInstanceOf(TranslationProviderUnavailableException::class.java)
    }

    @Test
    fun `maps 401 status to TranslationProviderAuthenticationException`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val command = ProviderTranslationCommand(
            text = "Text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThatThrownBy { adapter.translate(command) }
            .isInstanceOf(TranslationProviderAuthenticationException::class.java)
    }

    @Test
    fun `maps 429 status to TranslationProviderQuotaExceededException`() {
        mockServer.expect(requestTo("http://localhost:5000/translate"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        val command = ProviderTranslationCommand(
            text = "Text",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThatThrownBy { adapter.translate(command) }
            .isInstanceOf(TranslationProviderQuotaExceededException::class.java)
    }
}
