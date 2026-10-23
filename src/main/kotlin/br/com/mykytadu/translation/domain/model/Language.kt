package br.com.mykytadu.translation.domain.model

enum class Language(val code: String) {
    EN("en"),
    JA("ja"),
    AUTO("auto"),
    PT_BR("pt-BR"),
    ;

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported language code: $code")

        fun fromCodeOrNull(code: String): Language? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
