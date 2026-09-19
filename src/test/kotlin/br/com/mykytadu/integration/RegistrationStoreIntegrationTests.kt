package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.RegistrationConflictException
import br.com.mykytadu.identity.application.port.out.RegistrationStore
import br.com.mykytadu.identity.application.port.out.VerificationTokenDraft
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class RegistrationStoreIntegrationTests(
    @Autowired private val store: RegistrationStore,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.action_tokens, identity.roles, identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `persists account and verification hash atomically`() {
        val fixture = fixture(1, "person@example.com", "a")

        store.create(fixture.account, fixture.token)

        assertThat(count("identity.users")).isEqualTo(1)
        assertThat(count("identity.password_credentials")).isEqualTo(1)
        assertThat(count("identity.roles")).isEqualTo(1)
        assertThat(count("identity.action_tokens")).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT token_hash FROM identity.action_tokens",
                String::class.java,
            ),
        ).isEqualTo("a".repeat(64)).doesNotContain("raw")
    }

    @Test
    fun `rolls back the complete duplicate registration`() {
        val first = fixture(1, "Person@Example.COM", "a")
        val duplicate = fixture(2, "person@example.com", "b")
        store.create(first.account, first.token)

        assertThatThrownBy { store.create(duplicate.account, duplicate.token) }
            .isInstanceOf(RegistrationConflictException::class.java)

        assertThat(count("identity.users")).isEqualTo(1)
        assertThat(count("identity.password_credentials")).isEqualTo(1)
        assertThat(count("identity.roles")).isEqualTo(1)
        assertThat(count("identity.action_tokens")).isEqualTo(1)
    }

    @Test
    fun `invalidates the previous token and binds replacement to the locked account`() {
        val first = fixture(1, "Person@Example.COM", "a")
        store.create(first.account, first.token)
        val replacement = VerificationTokenDraft(
            id = actionTokenId(2),
            tokenHash = TokenHash.sha256("b".repeat(64)),
            createdAt = NOW.plusSeconds(60),
            expiresAt = NOW.plusSeconds(86_460),
        )

        val recipient = store.replaceVerificationToken(
            Email.from("person@example.com"),
            replacement,
            NOW.plusSeconds(60),
        )

        assertThat(recipient).isEqualTo("Person@Example.COM")
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.action_tokens WHERE consumed_at IS NULL",
                Long::class.java,
            ),
        ).isEqualTo(1)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT user_id FROM identity.action_tokens WHERE token_hash = ?",
                UUID::class.java,
                "b".repeat(64),
            ),
        ).isEqualTo(first.account.user.id.value)
    }

    private fun fixture(sequence: Int, email: String, hashCharacter: String): RegistrationFixture {
        val userId = userId(sequence)
        val account = UserAccount.pending(
            id = userId,
            email = Email.from(email),
            displayName = "Person",
            passwordHash = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}fixture-$sequence"),
            now = NOW,
        )
        val token = ActionToken.emailVerification(
            id = actionTokenId(sequence),
            userId = userId,
            tokenHash = TokenHash.sha256(hashCharacter.repeat(64)),
            createdAt = NOW,
            expiresAt = NOW.plusSeconds(86_400),
        )
        return RegistrationFixture(account, token)
    }

    private fun userId(sequence: Int): UserId = UserId.from(
        UUID.fromString("0199204a-1200-7001-8000-0000000000${sequence.toString().padStart(2, '0')}"),
    )

    private fun actionTokenId(sequence: Int): ActionTokenId = ActionTokenId.from(
        UUID.fromString("0199204a-1200-7002-8000-0000000000${sequence.toString().padStart(2, '0')}"),
    )

    private fun count(table: String): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!

    private data class RegistrationFixture(val account: UserAccount, val token: ActionToken)

    companion object {
        private val NOW = Instant.parse("2026-09-19T12:00:00Z")

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
