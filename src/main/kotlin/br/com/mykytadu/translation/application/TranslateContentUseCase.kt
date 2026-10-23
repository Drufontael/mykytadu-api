package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslatedContent
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.api.TranslationService
import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.exception.InvalidContentException
import br.com.mykytadu.translation.domain.exception.TranslationProviderException
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.service.ContentNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TranslateContentUseCase(private val provider: TranslationProvider) : TranslationService {

    override fun translate(command: TranslateContentCommand): TranslationOutcome {
        val normalized = try {
            ContentNormalizer.normalize(command.text)
        } catch (ex: InvalidContentException) {
            return TranslationOutcome.InvalidContent(ex.message ?: "Invalid content")
        }

        val sourceLang = Language.fromCode(command.sourceLanguage)
        val targetLang = Language.fromCode(command.targetLanguage)
        val contentType = ContentType.fromValue(command.contentType)

        val providerCommand = ProviderTranslationCommand(
            text = normalized.normalizedText.value,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            contentType = contentType,
            format = normalized.format,
        )

        return executeProviderTranslation(providerCommand, sourceLang, targetLang, contentType)
    }

    private fun executeProviderTranslation(
        providerCommand: ProviderTranslationCommand,
        sourceLanguage: Language,
        targetLanguage: Language,
        contentType: ContentType,
    ): TranslationOutcome = try {
        val providerResult = provider.translate(providerCommand)
        val resolvedSource = providerResult.detectedSourceLanguage ?: sourceLanguage
        val response = TranslatedContent(
            translation = providerResult.translatedText,
            sourceLanguage = resolvedSource.code,
            targetLanguage = targetLanguage.code,
            contentType = contentType.value,
            cached = false,
        )
        TranslationOutcome.Success(response)
    } catch (ex: TranslationProviderTimeoutException) {
        logger.warn("Translation timed out: {}", ex.message)
        TranslationOutcome.Timeout
    } catch (ex: TranslationProviderException) {
        logger.warn("Translation provider failed: {}", ex.message)
        TranslationOutcome.ProviderFailed(ex.message)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranslateContentUseCase::class.java)
    }
}
