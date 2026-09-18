package br.com.mykytadu.identity.domain.model

enum class Role {
    USER,
    ADMIN,
    ;

    companion object {

        fun fromPersistenceValue(value: String): Role = entries.firstOrNull {
            it.name == value
        } ?: throw IllegalArgumentException("Unsupported role")
    }
}
