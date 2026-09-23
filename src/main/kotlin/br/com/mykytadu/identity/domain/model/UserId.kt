package br.com.mykytadu.identity.domain.model

import java.util.UUID

@JvmInline
value class UserId private constructor(val value: UUID) {

    companion object {

        fun from(value: UUID): UserId {
            require(value.version() == UUID_VERSION_SEVEN) { "User id must be a UUIDv7" }
            return UserId(value)
        }

        private const val UUID_VERSION_SEVEN = 7
    }
}
