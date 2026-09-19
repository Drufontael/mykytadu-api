package br.com.mykytadu.identity.domain.model

import java.util.Locale

class Email private constructor(val address: String, val normalized: String) {

    override fun equals(other: Any?): Boolean = other is Email && normalized == other.normalized

    override fun hashCode(): Int = normalized.hashCode()

    override fun toString(): String = address

    companion object {

        fun from(value: String): Email {
            val address = value.trim()
            require(address.length in MIN_LENGTH..MAX_LENGTH) {
                "Email length must be between $MIN_LENGTH and $MAX_LENGTH characters"
            }
            val atIndex = address.indexOf('@')
            require(atIndex > 0 && atIndex == address.lastIndexOf('@') && atIndex < address.lastIndex) {
                "Email must contain one @ between the local and domain parts"
            }
            return Email(address, address.lowercase(Locale.ROOT))
        }

        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 254
    }
}
