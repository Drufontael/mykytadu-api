package br.com.mykytadu.translation.domain.model

enum class ContentType(val value: String) {
    ANIME_DESCRIPTION("ANIME_DESCRIPTION"),
    ;

    companion object {
        fun fromValue(value: String): ContentType = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported content type: $value")

        fun fromValueOrNull(value: String): ContentType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}
