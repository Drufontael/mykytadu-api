package br.com.mykytadu.identity.domain.model

import java.time.Instant
import java.util.UUID

class ActionToken private constructor(
    val id: ActionTokenId,
    val userId: UserId,
    val type: ActionTokenType,
    val tokenHash: TokenHash,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val createdAt: Instant,
) {

    init {
        require(expiresAt.isAfter(createdAt)) { "Action token expiry must be after creation time" }
        require(consumedAt == null || !consumedAt.isBefore(createdAt)) {
            "Action token consumption time must not precede creation time"
        }
    }

    fun isConsumableAt(instant: Instant): Boolean =
        consumedAt == null && !instant.isBefore(createdAt) && expiresAt.isAfter(instant)

    fun consume(consumedAt: Instant): ActionToken {
        check(isConsumableAt(consumedAt)) { "Action token cannot be consumed" }
        return ActionToken(id, userId, type, tokenHash, expiresAt, consumedAt, createdAt)
    }

    companion object {

        fun emailVerification(
            id: ActionTokenId,
            userId: UserId,
            tokenHash: TokenHash,
            createdAt: Instant,
            expiresAt: Instant,
        ): ActionToken = ActionToken(
            id = id,
            userId = userId,
            type = ActionTokenType.EMAIL_VERIFICATION,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            consumedAt = null,
            createdAt = createdAt,
        )

        fun restore(
            id: ActionTokenId,
            userId: UserId,
            type: ActionTokenType,
            tokenHash: TokenHash,
            expiresAt: Instant,
            consumedAt: Instant?,
            createdAt: Instant,
        ): ActionToken = ActionToken(id, userId, type, tokenHash, expiresAt, consumedAt, createdAt)
    }
}

@JvmInline
value class ActionTokenId private constructor(val value: UUID) {

    companion object {

        fun from(value: UUID): ActionTokenId {
            require(value.version() == UUID_VERSION_SEVEN) { "Action token id must be a UUIDv7" }
            return ActionTokenId(value)
        }

        private const val UUID_VERSION_SEVEN = 7
    }
}

enum class ActionTokenType(val persistenceValue: String) {
    EMAIL_VERIFICATION("email_verification"),
    PASSWORD_RESET("password_reset"),

    ;

    companion object {
        fun fromPersistenceValue(value: String): ActionTokenType = entries.firstOrNull { it.persistenceValue == value }
            ?: throw IllegalArgumentException("Unsupported action token type")
    }
}

class TokenHash private constructor(private val value: String) {

    internal fun persistenceValue(): String = value

    override fun equals(other: Any?): Boolean = other is TokenHash && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "[REDACTED]"

    companion object {

        fun sha256(value: String): TokenHash {
            require(SHA_256_HEX.matches(value)) { "Token hash must be a SHA-256 hexadecimal value" }
            return TokenHash(value)
        }

        private val SHA_256_HEX = Regex("^[a-f0-9]{64}$")
    }
}
