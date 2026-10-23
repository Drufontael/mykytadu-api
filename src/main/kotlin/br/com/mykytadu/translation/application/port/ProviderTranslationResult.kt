package br.com.mykytadu.translation.application.port

import br.com.mykytadu.translation.domain.model.Language

data class ProviderTranslationResult(
    val translatedText: String,
    val detectedSourceLanguage: Language? = null,
    val provider: String,
    val modelVersion: String? = null,
) {
    override fun toString(): String = "ProviderTranslationResult(provider=$provider, model=$modelVersion, " +
        "detected=$detectedSourceLanguage, length=${translatedText.length})"
}
