package br.com.mykytadu.translation.domain.model

data class TranslationSource(val system: String, val externalId: String) {
    init {
        require(system.isNotBlank() && system.length <= MAX_SYSTEM_LENGTH) {
            "TranslationSource system length must be between 1 and $MAX_SYSTEM_LENGTH"
        }
        require(externalId.isNotBlank() && externalId.length <= MAX_EXTERNAL_ID_LENGTH) {
            "TranslationSource externalId length must be between 1 and $MAX_EXTERNAL_ID_LENGTH"
        }
    }

    companion object {
        const val MAX_SYSTEM_LENGTH = 64
        const val MAX_EXTERNAL_ID_LENGTH = 128
    }
}
