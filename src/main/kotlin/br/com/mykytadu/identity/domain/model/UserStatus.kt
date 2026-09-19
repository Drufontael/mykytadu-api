package br.com.mykytadu.identity.domain.model

enum class UserStatus(val persistenceValue: String) {
    PENDING("pending"),
    ACTIVE("active"),
    BLOCKED("blocked"),
    DELETED("deleted"),
    ;

    companion object {

        fun fromPersistenceValue(value: String): UserStatus = entries.firstOrNull {
            it.persistenceValue == value
        } ?: throw IllegalArgumentException("Unsupported user status")
    }
}
