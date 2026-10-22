package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.PasswordRecoveryStore
import br.com.mykytadu.identity.application.port.out.PasswordResetTokenDraft
import br.com.mykytadu.identity.application.port.out.RegistrationStore
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
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
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class PasswordRecoveryStoreIntegrationTests(
    @Autowired private val registrationStore: RegistrationStore,
    @Autowired private val recoveryStore: PasswordRecoveryStore,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `persists password reset token and invalidates previous reset tokens for active user`() {
        val account = activeUserAccount("person@example.com")
        val verificationToken = ActionToken.emailVerification(
            id = actionTokenId(1),
            userId = account.user.id,
            tokenHash = TokenHash.sha256("a".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(86_400),
        )
        registrationStore.create(account, verificationToken)

        jdbcTemplate.update(
            "UPDATE identity.users SET status = 'active', email_verified_at = ? WHERE id = ?",
            Timestamp.from(NOW),
            account.user.id.value,
        )

        val firstDraft = PasswordResetTokenDraft(
            id = actionTokenId(2),
            tokenHash = TokenHash.sha256("b".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(7_200),
        )
        val recipient1 = recoveryStore.createPasswordResetToken(account.user.email, firstDraft, NOW)
        assertThat(recipient1).isEqualTo("person@example.com")

        val secondDraft = PasswordResetTokenDraft(
            id = actionTokenId(3),
            tokenHash = TokenHash.sha256("c".repeat(64)),
            createdAt = NOW.plusSeconds(60),
            expiresAt = NOW.plusSeconds(7_260),
        )
        val recipient2 = recoveryStore.createPasswordResetToken(account.user.email, secondDraft, NOW.plusSeconds(60))
        assertThat(recipient2).isEqualTo("person@example.com")

        val firstConsumedAt = jdbcTemplate.queryForObject(
            "SELECT consumed_at FROM identity.action_tokens WHERE id = ?",
            Instant::class.java,
            firstDraft.id.value,
        )
        assertThat(firstConsumedAt).isEqualTo(NOW.plusSeconds(60))

        val secondConsumedAt = jdbcTemplate.query(
            "SELECT consumed_at FROM identity.action_tokens WHERE id = ?",
            { rs, _ -> rs.getTimestamp("consumed_at")?.toInstant() },
            secondDraft.id.value,
        ).singleOrNull()
        assertThat(secondConsumedAt).isNull()

        val verificationConsumedAt = jdbcTemplate.query(
            "SELECT consumed_at FROM identity.action_tokens WHERE id = ?",
            { rs, _ -> rs.getTimestamp("consumed_at")?.toInstant() },
            verificationToken.id.value,
        ).singleOrNull()
        assertThat(verificationConsumedAt).isNull()
    }

    @Test
    fun `returns null and does not persist token when user does not exist`() {
        val draft = PasswordResetTokenDraft(
            id = actionTokenId(1),
            tokenHash = TokenHash.sha256("b".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(7_200),
        )
        val recipient = recoveryStore.createPasswordResetToken(Email.from("unknown@example.com"), draft, NOW)

        assertThat(recipient).isNull()
        assertThat(count("identity.action_tokens")).isEqualTo(0)
    }

    @Test
    fun `returns null and does not persist token when user is pending`() {
        val account = UserAccount.pending(
            id = userId(1),
            email = Email.from("pending@example.com"),
            displayName = "Pending",
            passwordHash = PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$mock\$mock"),
            now = NOW,
        )
        val verificationToken = ActionToken.emailVerification(
            id = actionTokenId(1),
            userId = account.user.id,
            tokenHash = TokenHash.sha256("a".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(86_400),
        )
        registrationStore.create(account, verificationToken)

        val draft = PasswordResetTokenDraft(
            id = actionTokenId(2),
            tokenHash = TokenHash.sha256("b".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(7_200),
        )
        val recipient = recoveryStore.createPasswordResetToken(account.user.email, draft, NOW)

        assertThat(recipient).isNull()
        assertThat(count("identity.action_tokens")).isEqualTo(1)
    }

    @Test
    fun `resets password, marks token consumed, updates credential, and revokes sessions for active user`() {
        val account = createActiveUser("person@example.com", sequence = 1)
        val resetTokenDraft = PasswordResetTokenDraft(
            id = actionTokenId(2),
            tokenHash = TokenHash.sha256("b".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(7_200),
        )
        recoveryStore.createPasswordResetToken(account.user.email, resetTokenDraft, NOW)

        val sessionId = UUID.fromString("0199204a-1200-7003-8000-000000000001")
        val familyId = UUID.fromString("0199204a-1200-7004-8000-000000000001")
        insertActiveSession(account.user.id.value, sessionId, familyId)

        val resetAt = NOW.plusSeconds(600)
        val newPasswordHash = PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$new-mock\$new-mock")
        val resetOutcome = recoveryStore.resetPassword(resetTokenDraft.tokenHash, newPasswordHash, resetAt)

        assertThat(resetOutcome).isTrue()
        assertThat(queryConsumedAt(resetTokenDraft.id.value)).isEqualTo(resetAt)
        assertThat(queryPasswordHash(account.user.id.value)).isEqualTo(newPasswordHash.encodedValue())

        val sessionRevocation = jdbcTemplate.queryForMap(
            "SELECT revoked_at, revoke_reason FROM identity.sessions WHERE id = ?",
            sessionId,
        )
        assertThat((sessionRevocation["revoked_at"] as Timestamp).toInstant()).isEqualTo(resetAt)
        assertThat(sessionRevocation["revoke_reason"]).isEqualTo("logout_all")

        val secondAttempt = recoveryStore.resetPassword(
            resetTokenDraft.tokenHash,
            PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$another\$another"),
            resetAt.plusSeconds(60),
        )
        assertThat(secondAttempt).isFalse()
    }

    @Test
    fun `fails to reset password when token is expired`() {
        val account = createActiveUser("person2@example.com", sequence = 2)
        val resetTokenDraft = PasswordResetTokenDraft(
            id = actionTokenId(11),
            tokenHash = TokenHash.sha256("d".repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(7_200),
        )
        recoveryStore.createPasswordResetToken(account.user.email, resetTokenDraft, NOW)

        val expiredAt = resetTokenDraft.expiresAt.plusSeconds(1)
        val newPasswordHash = PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$new-mock\$new-mock")
        val resetOutcome = recoveryStore.resetPassword(resetTokenDraft.tokenHash, newPasswordHash, expiredAt)

        assertThat(resetOutcome).isFalse()
    }

    private fun createActiveUser(email: String, sequence: Int): UserAccount {
        val account = activeUserAccount(email, sequence)
        val verificationToken = ActionToken.emailVerification(
            id = actionTokenId(sequence * 10),
            userId = account.user.id,
            tokenHash = TokenHash.sha256(sequence.toString().repeat(64).take(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(86_400),
        )
        registrationStore.create(account, verificationToken)
        jdbcTemplate.update(
            "UPDATE identity.users SET status = 'active', email_verified_at = ? WHERE id = ?",
            Timestamp.from(NOW),
            account.user.id.value,
        )
        return account
    }

    private fun insertActiveSession(userId: UUID, sessionId: UUID, familyId: UUID) {
        jdbcTemplate.update(
            """
                INSERT INTO identity.sessions (
                    id, user_id, refresh_token_hash, token_family_id, client_id,
                    csrf_token_hash, expires_at, created_at
                ) VALUES (?, ?, ?, ?, 'mykytadu-android', NULL, ?, ?)
            """.trimIndent(),
            sessionId,
            userId,
            "c".repeat(64),
            familyId,
            Timestamp.from(NOW.plusSeconds(86_400)),
            Timestamp.from(NOW),
        )
    }

    private fun queryConsumedAt(tokenId: UUID): Instant? = jdbcTemplate.queryForObject(
        "SELECT consumed_at FROM identity.action_tokens WHERE id = ?",
        Timestamp::class.java,
        tokenId,
    )?.toInstant()

    private fun queryPasswordHash(userId: UUID): String? = jdbcTemplate.queryForObject(
        "SELECT password_hash FROM identity.password_credentials WHERE user_id = ?",
        String::class.java,
        userId,
    )

    private fun activeUserAccount(email: String, sequence: Int = 1): UserAccount = UserAccount.pending(
        id = userId(sequence),
        email = Email.from(email),
        displayName = "Active User",
        passwordHash = PasswordHash.from("\$argon2id\$v=19\$m=16,t=2,p=1\$mock\$mock"),
        now = NOW,
    )

    private fun userId(sequence: Int): UserId = UserId.from(
        UUID.fromString("0199204a-1200-7001-8000-0000000000${sequence.toString().padStart(2, '0')}"),
    )

    private fun actionTokenId(sequence: Int): ActionTokenId = ActionTokenId.from(
        UUID.fromString("0199204a-1200-7002-8000-0000000000${sequence.toString().padStart(2, '0')}"),
    )

    private fun count(table: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!

    companion object {
        private val NOW = Instant.parse("2026-10-18T10:00:00Z")

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
