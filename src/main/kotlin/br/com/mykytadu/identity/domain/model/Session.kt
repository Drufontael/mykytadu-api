package br.com.mykytadu.identity.domain.model

import java.time.Instant
import java.util.UUID

class Session private constructor(
    val id: SessionId,
    val userId: UserId,
    val refreshTokenHash: TokenHash,
    val tokenFamilyId: TokenFamilyId,
    val clientId: String?,
    val csrfTokenHash: TokenHash?,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val revokeReason: String?,
    val createdAt: Instant,
) {

    init {
        require(expiresAt.isAfter(createdAt)) { "Session expiry must be after creation time" }
        require((clientId == WEB_CLIENT_ID) == (csrfTokenHash != null)) {
            "Only Web sessions must contain a CSRF token hash"
        }
        require((revokedAt == null) == (revokeReason == null)) {
            "Session revocation time and reason must be provided together"
        }
        require(revokedAt == null || !revokedAt.isBefore(createdAt)) {
            "Session revocation time must not precede creation time"
        }
    }

    companion object {
        const val WEB_CLIENT_ID = "mykytadu-web"

        fun initial(
            id: SessionId,
            userId: UserId,
            refreshTokenHash: TokenHash,
            tokenFamilyId: TokenFamilyId,
            clientId: String?,
            csrfTokenHash: TokenHash?,
            createdAt: Instant,
            expiresAt: Instant,
        ): Session = Session(
            id,
            userId,
            refreshTokenHash,
            tokenFamilyId,
            clientId,
            csrfTokenHash,
            expiresAt,
            null,
            null,
            createdAt,
        )
    }
}

@JvmInline
value class SessionId private constructor(val value: UUID) {
    companion object {
        fun from(value: UUID): SessionId {
            require(value.version() == 7) { "Session id must be a UUIDv7" }
            return SessionId(value)
        }
    }
}

@JvmInline
value class TokenFamilyId private constructor(val value: UUID) {
    companion object {
        fun from(value: UUID): TokenFamilyId {
            require(value.version() == 7) { "Token family id must be a UUIDv7" }
            return TokenFamilyId(value)
        }
    }
}
