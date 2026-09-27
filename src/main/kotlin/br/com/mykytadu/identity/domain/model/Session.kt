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
    val parentSessionId: SessionId?,
    val rotationReplay: SessionRotationReplay?,
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
        require(parentSessionId != id) { "Session cannot be its own parent" }
        require((revokeReason == ROTATED_REASON) == (rotationReplay != null)) {
            "Only a rotated session must contain replay metadata"
        }
        rotationReplay?.let { replay ->
            require(replay.successorSessionId != id) { "Session cannot rotate to itself" }
            requireNotNull(revokedAt) { "Rotated session must have a revocation time" }
            require(replay.replayUntil.isAfter(revokedAt)) { "Replay window must end after rotation" }
            require(replay.accessExpiresAt.isAfter(replay.accessIssuedAt)) {
                "Replay access token expiry must be after issuance"
            }
        }
    }

    fun markRotated(rotatedAt: Instant, replay: SessionRotationReplay): Session {
        check(isActiveAt(rotatedAt)) { "Only an active session can rotate" }
        return Session(
            id,
            userId,
            refreshTokenHash,
            tokenFamilyId,
            clientId,
            csrfTokenHash,
            expiresAt,
            rotatedAt,
            ROTATED_REASON,
            createdAt,
            parentSessionId,
            replay,
        )
    }

    fun isActiveAt(instant: Instant): Boolean =
        revokedAt == null && !instant.isBefore(createdAt) && instant.isBefore(expiresAt)

    fun isReplayAllowed(idempotencyKeyHash: TokenHash, instant: Instant): Boolean = rotationReplay?.let { replay ->
        replay.idempotencyKeyHash == idempotencyKeyHash && instant.isBefore(replay.replayUntil)
    } ?: false

    fun reissueCsrf(csrfTokenHash: TokenHash): Session {
        check(clientId == WEB_CLIENT_ID && revokedAt == null) { "Only an active Web session can reissue CSRF" }
        return Session(
            id,
            userId,
            refreshTokenHash,
            tokenFamilyId,
            clientId,
            csrfTokenHash,
            expiresAt,
            revokedAt,
            revokeReason,
            createdAt,
            parentSessionId,
            rotationReplay,
        )
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
            null,
            null,
        )

        fun successor(
            id: SessionId,
            predecessorId: SessionId,
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
            predecessorId,
            null,
        )

        fun restore(
            id: SessionId,
            userId: UserId,
            refreshTokenHash: TokenHash,
            tokenFamilyId: TokenFamilyId,
            clientId: String?,
            csrfTokenHash: TokenHash?,
            expiresAt: Instant,
            revokedAt: Instant?,
            revokeReason: String?,
            createdAt: Instant,
            parentSessionId: SessionId?,
            rotationReplay: SessionRotationReplay?,
        ): Session = Session(
            id,
            userId,
            refreshTokenHash,
            tokenFamilyId,
            clientId,
            csrfTokenHash,
            expiresAt,
            revokedAt,
            revokeReason,
            createdAt,
            parentSessionId,
            rotationReplay,
        )

        const val ROTATED_REASON = "rotated"
    }
}

data class SessionRotationReplay(
    val successorSessionId: SessionId,
    val idempotencyKeyHash: TokenHash,
    val derivationKeyId: String,
    val replayUntil: Instant,
    val accessTokenId: UUID,
    val accessIssuedAt: Instant,
    val accessExpiresAt: Instant,
) {
    init {
        require(derivationKeyId.isNotBlank()) { "Refresh derivation key id must not be blank" }
        require(derivationKeyId.length <= 100) { "Refresh derivation key id must not exceed 100 characters" }
    }

    override fun toString(): String = "SessionRotationReplay([REDACTED])"
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
