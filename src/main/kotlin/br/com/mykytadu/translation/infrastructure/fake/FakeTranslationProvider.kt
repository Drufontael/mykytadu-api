package br.com.mykytadu.translation.infrastructure.fake

import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.ProviderTranslationResult
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.domain.exception.TranslationProviderException
import br.com.mykytadu.translation.domain.model.Language

class FakeTranslationProvider(
    private val cannedResponses: MutableMap<String, String> = mutableMapOf(),
    var errorToThrow: TranslationProviderException? = null,
    var simulatedDelayMillis: Long = 0,
    var defaultDetectedLanguage: Language = Language.EN,
) : TranslationProvider {

    override fun translate(command: ProviderTranslationCommand): ProviderTranslationResult {
        errorToThrow?.let { throw it }
        if (simulatedDelayMillis > 0) {
            Thread.sleep(simulatedDelayMillis)
        }
        val translated = cannedResponses[command.text] ?: when (command.targetLanguage) {
            Language.PT_BR -> "[PT-BR] ${command.text}"
            else -> "[${command.targetLanguage.code}] ${command.text}"
        }
        val detected = if (command.sourceLanguage == Language.AUTO) defaultDetectedLanguage else command.sourceLanguage
        return ProviderTranslationResult(
            translatedText = translated,
            detectedSourceLanguage = detected,
            provider = PROVIDER_NAME,
            modelVersion = MODEL_VERSION,
        )
    }

    fun addCannedResponse(original: String, translated: String) {
        cannedResponses[original] = translated
    }

    fun clear() {
        cannedResponses.clear()
        errorToThrow = null
        simulatedDelayMillis = 0
    }

    companion object {
        const val PROVIDER_NAME = "fake-provider"
        const val MODEL_VERSION = "fake-1.0"
    }
}
