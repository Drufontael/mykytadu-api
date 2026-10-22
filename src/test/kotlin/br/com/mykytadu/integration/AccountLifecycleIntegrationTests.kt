package br.com.mykytadu.integration

import br.com.mykytadu.identity.api.AccountLifecycleOutcome
import br.com.mykytadu.identity.api.BlockAccountCommand
import br.com.mykytadu.identity.api.DeleteAccountCommand
import br.com.mykytadu.identity.api.IdentityAccountLifecycle
import br.com.mykytadu.identity.application.port.out.RegistrationStore
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class AccountLifecycleIntegrationTests(
    @Autowired private val lifecycle: IdentityAccountLifecycle,
    @Autowired private val registrationStore: RegistrationStore,
    @Autowired private val sessions: SessionStore,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val clock: Clock,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `blocks account under lock and immediately revokes all active renewable sessions`() {
        val user = createActiveUser("block-me@example.com", 1)
        val sessionId = UUID.fromString("0199204a-1200-7003-8000-000000000001")
        val familyId = UUID.fromString("0199204a-1200-7004-8000-000000000001")
        insertActiveSession(user, sessionId, familyId, 1)

        val outcome = lifecycle.block(BlockAccountCommand(user.value))
        assertThat(outcome).isEqualTo(AccountLifecycleOutcome.Blocked(user.value))

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM identity.users WHERE id = ?",
            String::class.java,
            user.value,
        )
        assertThat(status).isEqualTo("blocked")

        val session = jdbcTemplate.queryForMap(
            "SELECT revoked_at, revoke_reason FROM identity.sessions WHERE id = ?",
            sessionId,
        )
        assertThat(session["revoked_at"]).isNotNull
        assertThat(session["revoke_reason"]).isEqualTo("account_inactive")
    }

    @Test
    fun `deletes account under lock and immediately revokes all active renewable sessions`() {
        val user = createActiveUser("delete-me@example.com", 2)
        val sessionId = UUID.fromString("0199204a-1200-7003-8000-000000000002")
        val familyId = UUID.fromString("0199204a-1200-7004-8000-000000000002")
        insertActiveSession(user, sessionId, familyId, 2)

        val outcome = lifecycle.delete(DeleteAccountCommand(user.value))
        assertThat(outcome).isEqualTo(AccountLifecycleOutcome.Deleted(user.value))

        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM identity.users WHERE id = ?",
            String::class.java,
            user.value,
        )
        assertThat(status).isEqualTo("deleted")

        val session = jdbcTemplate.queryForMap(
            "SELECT revoked_at, revoke_reason FROM identity.sessions WHERE id = ?",
            sessionId,
        )
        assertThat(session["revoked_at"]).isNotNull
        assertThat(session["revoke_reason"]).isEqualTo("account_inactive")
    }

    private fun createActiveUser(email: String, sequence: Int): UserId {
        val baseTime = clock.instant().minusSeconds(60)
        val userId = UserId.from(
            UUID.fromString("0199204a-1200-7001-8000-0000000000${sequence.toString().padStart(2, '0')}"),
        )
        val account = UserAccount.pending(
            id = userId,
            email = Email.from(email),
            displayName = "Lifecycle User",
            passwordHash = PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$mock\$mock"),
            now = baseTime,
        )
        val token = ActionToken.emailVerification(
            id = ActionTokenId.from(
                UUID.fromString("0199204a-1200-7002-8000-0000000000${sequence.toString().padStart(2, '0')}"),
            ),
            userId = userId,
            tokenHash = TokenHash.sha256("a".repeat(62) + sequence.toString().padStart(2, '0')),
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(86_400),
        )
        registrationStore.create(account, token)
        jdbcTemplate.update(
            "UPDATE identity.users SET status = 'active', email_verified_at = ? WHERE id = ?",
            Timestamp.from(baseTime),
            userId.value,
        )
        return userId
    }

    private fun insertActiveSession(userId: UserId, sessionId: UUID, familyId: UUID, sequence: Int) {
        val baseTime = clock.instant().minusSeconds(60)
        sessions.create(
            Session.initial(
                id = SessionId.from(sessionId),
                userId = userId,
                refreshTokenHash = TokenHash.sha256("b".repeat(62) + sequence.toString().padStart(2, '0')),
                tokenFamilyId = TokenFamilyId.from(familyId),
                clientId = "mykytadu-android",
                csrfTokenHash = null,
                createdAt = baseTime,
                expiresAt = clock.instant().plusSeconds(3_600),
            ),
        )
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
