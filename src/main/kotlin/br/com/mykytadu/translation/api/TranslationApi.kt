package br.com.mykytadu.translation.api

import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.model.TranslationResult

data class TranslateContentCommand(
    val text: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val contentType: ContentType,
    val source: TranslationSourceInfo? = null,
) {
    override fun toString(): String = "TranslateContentCommand(source=$sourceLanguage, target=$targetLanguage, " +
        "type=$contentType, sourceInfo=$source, textLength=${text.length})"
}

data class TranslationSourceInfo(val system: String, val externalId: String)

sealed interface TranslationOutcome {
    data class Success(val result: TranslationResult) : TranslationOutcome
    data class InvalidContent(val message: String, val field: String = "text") : TranslationOutcome
    data object Timeout : TranslationOutcome
    data class ProviderFailed(val message: String? = null) : TranslationOutcome
}

interface TranslationService {
    fun translate(command: TranslateContentCommand): TranslationOutcome
}
