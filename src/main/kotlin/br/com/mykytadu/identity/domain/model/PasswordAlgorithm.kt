package br.com.mykytadu.identity.domain.model

enum class PasswordAlgorithm(val persistenceValue: String) {
    ARGON2ID("argon2id"),
    ;

    companion object {

        fun fromPersistenceValue(value: String): PasswordAlgorithm = entries.firstOrNull {
            it.persistenceValue == value
        } ?: throw IllegalArgumentException("Unsupported password algorithm")
    }
}
