package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.api.TranslationService
import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.exception.InvalidContentException
import br.com.mykytadu.translation.domain.exception.TranslationProviderException
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.model.TranslationResult
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

        val providerCommand = ProviderTranslationCommand(
            text = normalized.normalizedText.value,
            sourceLanguage = command.sourceLanguage,
            targetLanguage = command.targetLanguage,
            contentType = command.contentType,
            format = normalized.format,
        )

        return executeProviderTranslation(providerCommand, command)
    }

    private fun executeProviderTranslation(
        providerCommand: ProviderTranslationCommand,
        originalCommand: TranslateContentCommand,
    ): TranslationOutcome = try {
        val providerResult = provider.translate(providerCommand)
        val resolvedSource = providerResult.detectedSourceLanguage ?: originalCommand.sourceLanguage
        val result = TranslationResult(
            translation = providerResult.translatedText,
            sourceLanguage = resolvedSource,
            targetLanguage = originalCommand.targetLanguage,
            contentType = originalCommand.contentType,
            cached = false,
            provider = providerResult.provider,
            modelVersion = providerResult.modelVersion,
        )
        TranslationOutcome.Success(result)
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
