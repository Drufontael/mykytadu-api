package br.com.mykytadu.translation.application.port

import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.model.TranslationFormat

data class ProviderTranslationCommand(
    val text: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val contentType: ContentType,
    val format: TranslationFormat = TranslationFormat.TEXT,
) {
    override fun toString(): String = "ProviderTranslationCommand(source=$sourceLanguage, target=$targetLanguage, " +
        "type=$contentType, format=$format, textLength=${text.length})"
}
