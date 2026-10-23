package br.com.mykytadu.api.translation

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class TranslationRequestDto(
    @field:NotBlank(message = "must not be blank")
    @field:Size(min = 1, max = 10000, message = "must contain between 1 and 10000 characters")
    val text: String,
    @field:Pattern(regexp = "^(en|ja|auto)$", message = "must be one of: en, ja, auto")
    val sourceLanguage: String,
    @field:Pattern(regexp = "^(pt-BR)$", message = "must be: pt-BR")
    val targetLanguage: String,
    @field:Pattern(regexp = "^(ANIME_DESCRIPTION)$", message = "must be: ANIME_DESCRIPTION")
    val contentType: String,
    @field:Valid
    val source: TranslationSourceDto? = null,
) {
    override fun toString(): String = "TranslationRequestDto(source=$sourceLanguage, target=$targetLanguage, " +
        "type=$contentType, sourceInfo=$source, textLength=${text.length})"
}

data class TranslationSourceDto(
    @field:NotBlank(message = "must not be blank")
    @field:Size(min = 1, max = 64, message = "must contain between 1 and 64 characters")
    val system: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(min = 1, max = 128, message = "must contain between 1 and 128 characters")
    val externalId: String,
)

data class TranslationResponseDto(
    val translation: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val contentType: String,
    val cached: Boolean = false,
) {
    override fun toString(): String = "TranslationResponseDto(source=$sourceLanguage, target=$targetLanguage, " +
        "type=$contentType, cached=$cached, length=${translation.length})"
}
