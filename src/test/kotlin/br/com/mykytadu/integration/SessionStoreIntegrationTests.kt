package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.SessionRotationReplay
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class SessionStoreIntegrationTests(
    @Autowired private val sessions: SessionStore,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun prepareAccount() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
        jdbcTemplate.update(
            """
                INSERT INTO identity.users(
                    id, email, normalized_email, display_name, status,
                    email_verified_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'active', ?, ?, ?)
            """.trimIndent(),
            USER_ID.value,
            "session-store@example.test",
            "session-store@example.test",
            "Session Store",
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
        )
    }

    @Test
    fun `round trips a rotated predecessor and its successor`() {
        val predecessor = initialSession()
        val successor = Session.successor(
            id = SUCCESSOR_ID,
            predecessorId = predecessor.id,
            userId = predecessor.userId,
            refreshTokenHash = SUCCESSOR_HASH,
            tokenFamilyId = predecessor.tokenFamilyId,
            clientId = predecessor.clientId,
            csrfTokenHash = null,
            createdAt = ROTATED_AT,
            expiresAt = NOW.plusSeconds(7_200),
        )
        val replay = replay()

        sessions.create(predecessor)
        sessions.create(successor)
        sessions.update(predecessor.markRotated(ROTATED_AT, replay))

        val restoredPredecessor = sessions.findByRefreshTokenHashForUpdate(PREDECESSOR_HASH)
        val restoredSuccessor = sessions.findById(SUCCESSOR_ID)
        assertThat(restoredPredecessor?.rotationReplay).isEqualTo(replay)
        assertThat(restoredPredecessor?.revokeReason).isEqualTo(Session.ROTATED_REASON)
        assertThat(restoredSuccessor?.parentSessionId).isEqualTo(PREDECESSOR_ID)
        assertThat(restoredSuccessor?.tokenFamilyId).isEqualTo(FAMILY_ID)
    }

    @Test
    fun `database rejects incomplete rotation metadata`() {
        sessions.create(initialSession())

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                    UPDATE identity.sessions
                    SET revoked_at = ?, revoke_reason = 'rotated'
                    WHERE id = ?
                """.trimIndent(),
                Timestamp.from(ROTATED_AT),
                PREDECESSOR_ID.value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun initialSession(): Session = Session.initial(
        id = PREDECESSOR_ID,
        userId = USER_ID,
        refreshTokenHash = PREDECESSOR_HASH,
        tokenFamilyId = FAMILY_ID,
        clientId = "mykytadu-android",
        csrfTokenHash = null,
        createdAt = NOW,
        expiresAt = NOW.plusSeconds(3_600),
    )

    private fun replay() = SessionRotationReplay(
        successorSessionId = SUCCESSOR_ID,
        idempotencyKeyHash = IDEMPOTENCY_HASH,
        derivationKeyId = "refresh-key-1",
        replayUntil = ROTATED_AT.plusSeconds(120),
        accessTokenId = UUID.fromString("019937b6-3600-7001-8000-000000000014"),
        accessIssuedAt = ROTATED_AT,
        accessExpiresAt = ROTATED_AT.plusSeconds(600),
    )

    companion object {
        private val NOW = Instant.parse("2026-09-27T12:00:00Z")
        private val ROTATED_AT = NOW.plusSeconds(60)
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001"))
        private val PREDECESSOR_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        private val SUCCESSOR_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))
        private val FAMILY_ID = TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000012"))
        private val PREDECESSOR_HASH = TokenHash.sha256("a".repeat(64))
        private val SUCCESSOR_HASH = TokenHash.sha256("b".repeat(64))
        private val IDEMPOTENCY_HASH = TokenHash.sha256("c".repeat(64))

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
