package br.com.mykytadu.identity.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SessionTest {

    @Test
    fun `creates native and Web sessions with only hashed credentials`() {
        val native = session(clientId = "mykytadu-android", csrfHash = null)
        val web = session(clientId = Session.WEB_CLIENT_ID, csrfHash = CSRF_HASH)

        assertThat(native.refreshTokenHash.toString()).isEqualTo("[REDACTED]")
        assertThat(native.csrfTokenHash).isNull()
        assertThat(web.csrfTokenHash).isEqualTo(CSRF_HASH)
    }

    @Test
    fun `rejects csrf outside Web and missing csrf for Web`() {
        assertThatThrownBy { session(clientId = Session.WEB_CLIENT_ID, csrfHash = null) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { session(clientId = "mykytadu-ios", csrfHash = CSRF_HASH) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `preserves lineage and bounded replay metadata when rotating`() {
        val initial = session(clientId = "mykytadu-android", csrfHash = null)
        val replay = replay()

        val rotated = initial.markRotated(NOW.plusSeconds(30), replay)
        val successor = Session.successor(
            id = SUCCESSOR_ID,
            predecessorId = initial.id,
            userId = initial.userId,
            refreshTokenHash = SUCCESSOR_HASH,
            tokenFamilyId = initial.tokenFamilyId,
            clientId = initial.clientId,
            csrfTokenHash = null,
            createdAt = NOW.plusSeconds(30),
            expiresAt = NOW.plusSeconds(3_600),
        )

        assertThat(rotated.revokeReason).isEqualTo(Session.ROTATED_REASON)
        assertThat(rotated.rotationReplay).isEqualTo(replay)
        assertThat(rotated.isReplayAllowed(IDEMPOTENCY_HASH, NOW.plusSeconds(119))).isTrue()
        assertThat(rotated.isReplayAllowed(IDEMPOTENCY_HASH, NOW.plusSeconds(120))).isFalse()
        assertThat(successor.parentSessionId).isEqualTo(initial.id)
        assertThat(successor.tokenFamilyId).isEqualTo(initial.tokenFamilyId)
    }

    @Test
    fun `session expiration and replay boundaries are exact`() {
        val createdAt = NOW.plusSeconds(10)
        val expiresAt = NOW.plusSeconds(70)
        val initial = Session.initial(
            id = SESSION_ID,
            userId = USER_ID,
            refreshTokenHash = REFRESH_HASH,
            tokenFamilyId = FAMILY_ID,
            clientId = "mykytadu-android",
            csrfTokenHash = null,
            createdAt = createdAt,
            expiresAt = expiresAt,
        )
        val rotatedAt = NOW.plusSeconds(20)
        val replayUntil = rotatedAt.plusSeconds(120)
        val rotated = initial.markRotated(rotatedAt, replay(replayUntil))

        assertThat(initial.isActiveAt(createdAt.minusNanos(1))).isFalse()
        assertThat(initial.isActiveAt(createdAt)).isTrue()
        assertThat(initial.isActiveAt(expiresAt.minusNanos(1))).isTrue()
        assertThat(initial.isActiveAt(expiresAt)).isFalse()
        assertThat(rotated.isReplayAllowed(IDEMPOTENCY_HASH, replayUntil.minusNanos(1))).isTrue()
        assertThat(rotated.isReplayAllowed(IDEMPOTENCY_HASH, replayUntil)).isFalse()
    }

    @Test
    fun `rejects rotation of inactive session and invalid replay metadata`() {
        val initial = session(clientId = "mykytadu-android", csrfHash = null)
        val rotated = initial.markRotated(NOW.plusSeconds(30), replay())

        assertThatThrownBy { rotated.markRotated(NOW.plusSeconds(40), replay()) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy {
            replay(replayUntil = NOW.plusSeconds(30))
                .let { initial.markRotated(NOW.plusSeconds(30), it) }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun session(clientId: String?, csrfHash: TokenHash?): Session = Session.initial(
        SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010")),
        UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001")),
        REFRESH_HASH,
        TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011")),
        clientId,
        csrfHash,
        NOW,
        NOW.plusSeconds(60),
    )

    private fun replay(replayUntil: Instant = NOW.plusSeconds(120)) = SessionRotationReplay(
        successorSessionId = SUCCESSOR_ID,
        idempotencyKeyHash = IDEMPOTENCY_HASH,
        derivationKeyId = "refresh-key-1",
        replayUntil = replayUntil,
        accessTokenId = UUID.fromString("019937b6-3600-7001-8000-000000000013"),
        accessIssuedAt = NOW.plusSeconds(60),
        accessExpiresAt = NOW.plusSeconds(660),
    )

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val REFRESH_HASH = TokenHash.sha256("a".repeat(64))
        private val CSRF_HASH = TokenHash.sha256("b".repeat(64))
        private val SUCCESSOR_HASH = TokenHash.sha256("c".repeat(64))
        private val IDEMPOTENCY_HASH = TokenHash.sha256("d".repeat(64))
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001"))
        private val SESSION_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        private val FAMILY_ID = TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))
        private val SUCCESSOR_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000012"))
    }
}
