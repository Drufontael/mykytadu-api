package br.com.mykytadu.identity.domain.model

class PasswordHash private constructor(private val encoded: String) {

    internal fun encodedValue(): String = encoded

    override fun equals(other: Any?): Boolean = other is PasswordHash && encoded == other.encoded

    override fun hashCode(): Int = encoded.hashCode()

    override fun toString(): String = "[REDACTED]"

    companion object {

        fun from(encoded: String): PasswordHash {
            require(encoded.isNotBlank()) { "Password hash must not be blank" }
            return PasswordHash(encoded)
        }
    }
}
