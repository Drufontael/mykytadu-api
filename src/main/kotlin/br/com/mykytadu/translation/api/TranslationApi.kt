package br.com.mykytadu.translation.api

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class TranslateContentCommand(
    val text: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val contentType: String,
    val source: TranslationSourceInfo? = null,
) {
    override fun toString(): String =
        "TranslateContentCommand(source=$sourceLanguage, target=$targetLanguage, type=$contentType, " +
            "sourceInfo=$source, textLength=${text.length})"
}

@NamedInterface("api")
data class TranslationSourceInfo(val system: String, val externalId: String)

@NamedInterface("api")
sealed interface TranslationOutcome {
    @NamedInterface("api")
    data class Success(val response: TranslatedContent) : TranslationOutcome

    @NamedInterface("api")
    data class InvalidContent(val message: String, val field: String = "text") : TranslationOutcome

    @NamedInterface("api")
    data object Timeout : TranslationOutcome

    @NamedInterface("api")
    data class ProviderFailed(val message: String? = null) : TranslationOutcome
}

@NamedInterface("api")
data class TranslatedContent(
    val translation: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val contentType: String,
    val cached: Boolean,
) {
    override fun toString(): String =
        "TranslatedContent(sourceLanguage=$sourceLanguage, targetLanguage=$targetLanguage, " +
            "contentType=$contentType, cached=$cached, translationLength=${translation.length})"
}

@NamedInterface("api")
interface TranslationService {
    fun translate(command: TranslateContentCommand): TranslationOutcome
}
