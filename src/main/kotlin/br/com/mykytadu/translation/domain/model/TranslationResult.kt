package br.com.mykytadu.translation.domain.model

data class TranslationResult(
    val translation: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val contentType: ContentType,
    val cached: Boolean = false,
    val provider: String? = null,
    val modelVersion: String? = null,
) {
    override fun toString(): String =
        "TranslationResult(source=$sourceLanguage, target=$targetLanguage, type=$contentType, " +
            "cached=$cached, provider=$provider, length=${translation.length})"
}
