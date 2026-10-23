package br.com.mykytadu.translation.infrastructure.client

import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.ProviderTranslationResult
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.exception.TranslationProviderAuthenticationException
import br.com.mykytadu.translation.domain.exception.TranslationProviderException
import br.com.mykytadu.translation.domain.exception.TranslationProviderQuotaExceededException
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.exception.TranslationProviderUnavailableException
import br.com.mykytadu.translation.domain.model.Language
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpTimeoutException
import java.time.Duration

class LibreTranslateProviderAdapter(
    private val restClient: RestClient,
    private val properties: TranslationProviderProperties,
) : TranslationProvider {

    override fun translate(command: ProviderTranslationCommand): ProviderTranslationResult {
        val targetCode = when (command.targetLanguage) {
            Language.PT_BR -> "pt"
            else -> command.targetLanguage.code
        }
        val sourceCode = command.sourceLanguage.code

        val requestPayload = LibreTranslateRequest(
            q = command.text,
            source = sourceCode,
            target = targetCode,
            format = command.format.wireValue,
            apiKey = properties.apiKey?.takeIf { it.isNotBlank() },
        )

        val response = executeRequest(requestPayload)
        val detected = resolveDetectedLanguage(command.sourceLanguage, response.detectedLanguage?.language)

        return ProviderTranslationResult(
            translatedText = response.translatedText,
            detectedSourceLanguage = detected,
            provider = properties.providerName,
            modelVersion = properties.modelVersion,
        )
    }

    private fun executeRequest(payload: LibreTranslateRequest): LibreTranslateResponse = try {
        sendRequest(payload)
    } catch (ex: ResourceAccessException) {
        throw handleNetworkException(ex)
    } catch (ex: RestClientResponseException) {
        throw handleStatusException(ex.statusCode, ex)
    }

    private fun sendRequest(payload: LibreTranslateRequest): LibreTranslateResponse {
        val response = restClient.post()
            .uri("/translate")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(LibreTranslateResponse::class.java)

        return response ?: throw TranslationProviderUnavailableException("Empty response from provider")
    }

    private fun handleNetworkException(ex: ResourceAccessException): TranslationProviderException {
        logger.warn("Translation provider network failure: {}", ex.message)
        return if (ex.cause is HttpTimeoutException || ex.message?.contains("timeout", ignoreCase = true) == true) {
            TranslationProviderTimeoutException("Translation provider request timed out", ex)
        } else {
            TranslationProviderUnavailableException("Translation provider is unavailable", ex)
        }
    }

    private fun handleStatusException(
        statusCode: HttpStatusCode,
        ex: RestClientResponseException,
    ): TranslationProviderException {
        logger.warn("Translation provider returned status {}", statusCode)
        return when (statusCode.value()) {
            STATUS_UNAUTHORIZED, STATUS_FORBIDDEN ->
                TranslationProviderAuthenticationException("Translation provider credentials invalid", ex)

            STATUS_TOO_MANY_REQUESTS ->
                TranslationProviderQuotaExceededException("Translation provider quota exceeded", cause = ex)

            STATUS_GATEWAY_TIMEOUT ->
                TranslationProviderTimeoutException("Translation provider timed out with status 504", ex)

            else ->
                TranslationProviderUnavailableException("Translation provider failed with status $statusCode", ex)
        }
    }

    private fun resolveDetectedLanguage(sourceLanguage: Language, detectedCode: String?): Language =
        if (sourceLanguage != Language.AUTO) {
            sourceLanguage
        } else {
            detectedCode?.let { Language.fromCodeOrNull(it) } ?: Language.EN
        }

    companion object {
        private val logger = LoggerFactory.getLogger(LibreTranslateProviderAdapter::class.java)

        private const val STATUS_UNAUTHORIZED = 401
        private const val STATUS_FORBIDDEN = 403
        private const val STATUS_TOO_MANY_REQUESTS = 429
        private const val STATUS_GATEWAY_TIMEOUT = 504

        fun createDefault(
            properties: TranslationProviderProperties,
            restClientBuilder: RestClient.Builder = RestClient.builder(),
        ): LibreTranslateProviderAdapter {
            val requestFactory = JdkClientHttpRequestFactory().apply {
                setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds))
            }
            val client = restClientBuilder
                .baseUrl(properties.url)
                .requestFactory(requestFactory)
                .build()
            return LibreTranslateProviderAdapter(client, properties)
        }
    }
}
