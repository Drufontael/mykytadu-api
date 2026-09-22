package br.com.mykytadu.identity.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ActionTokenTest {

    @Test
    fun `creates an unconsumed email verification token`() {
        val createdAt = Instant.parse("2026-09-19T12:00:00Z")
        val token = ActionToken.emailVerification(
            id = TOKEN_ID,
            userId = USER_ID,
            tokenHash = TOKEN_HASH,
            createdAt = createdAt,
            expiresAt = createdAt.plusSeconds(86_400),
        )

        assertThat(token.type).isEqualTo(ActionTokenType.EMAIL_VERIFICATION)
        assertThat(token.consumedAt).isNull()
        assertThat(token.tokenHash.toString()).isEqualTo("[REDACTED]")
    }

    @Test
    fun `consumes a valid token only before its expiry`() {
        val createdAt = Instant.parse("2026-09-19T12:00:00Z")
        val token = ActionToken.emailVerification(
            TOKEN_ID,
            USER_ID,
            TOKEN_HASH,
            createdAt,
            createdAt.plusSeconds(60),
        )
        val consumedAt = createdAt.plusSeconds(30)

        val consumed = token.consume(consumedAt)

        assertThat(consumed.consumedAt).isEqualTo(consumedAt)
        assertThat(consumed.isConsumableAt(consumedAt)).isFalse()
        assertThat(token.isConsumableAt(createdAt.plusSeconds(60))).isFalse()
        assertThatThrownBy { token.consume(createdAt.plusSeconds(60)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Action token cannot be consumed")
    }

    @Test
    fun `rejects invalid action token identifiers hashes and expiration`() {
        val createdAt = Instant.parse("2026-09-19T12:00:00Z")

        assertThatThrownBy { ActionTokenId.from(UUID.randomUUID()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Action token id must be a UUIDv7")
        assertThatThrownBy { TokenHash.sha256("raw-token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Token hash must be a SHA-256 hexadecimal value")
        assertThatThrownBy {
            ActionToken.emailVerification(TOKEN_ID, USER_ID, TOKEN_HASH, createdAt, createdAt)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Action token expiry must be after creation time")
    }

    companion object {
        private val TOKEN_ID = ActionTokenId.from(UUID.fromString("0199204a-1200-7001-8000-000000000001"))
        private val USER_ID = UserId.from(UUID.fromString("0199204a-1200-7001-8000-000000000002"))
        private val TOKEN_HASH = TokenHash.sha256("a".repeat(64))
    }
}
