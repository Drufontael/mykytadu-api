package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.EmailVerificationStore
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class RegistrationStoreIntegrationTests(
    @Autowired private val store: RegistrationStore,
    @Autowired private val emailVerificationStore: EmailVerificationStore,
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

    @Test
    fun `consumes a verification token and activates its pending account only once`() {
        val fixture = fixture(1, "person@example.com", "a")
        val verifiedAt = NOW.plusSeconds(60)
        store.create(fixture.account, fixture.token)

        val firstAttempt = emailVerificationStore.verify(fixture.token.tokenHash, verifiedAt)
        val secondAttempt = emailVerificationStore.verify(fixture.token.tokenHash, verifiedAt.plusSeconds(1))

        assertThat(firstAttempt).isTrue()
        assertThat(secondAttempt).isFalse()
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM identity.users", String::class.java))
            .isEqualTo("active")
        assertThat(jdbcTemplate.queryForObject("SELECT email_verified_at FROM identity.users", Instant::class.java))
            .isEqualTo(verifiedAt)
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM identity.users", Instant::class.java))
            .isEqualTo(verifiedAt)
        assertThat(jdbcTemplate.queryForObject("SELECT consumed_at FROM identity.action_tokens", Instant::class.java))
            .isEqualTo(verifiedAt)
    }

    @Test
    fun `does not consume an expired verification token or activate its account`() {
        val fixture = fixture(1, "person@example.com", "a")
        store.create(fixture.account, fixture.token)

        val verified = emailVerificationStore.verify(fixture.token.tokenHash, fixture.token.expiresAt)

        assertThat(verified).isFalse()
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM identity.users", String::class.java))
            .isEqualTo("pending")
        assertThat(jdbcTemplate.queryForObject("SELECT consumed_at FROM identity.action_tokens", Instant::class.java))
            .isNull()
    }

    @Test
    fun `allows only one concurrent verification to consume the token`() {
        val fixture = fixture(1, "person@example.com", "a")
        val verifiedAt = NOW.plusSeconds(60)
        store.create(fixture.account, fixture.token)
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        try {
            val attempts = List(2) {
                executor.submit<Boolean> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "Concurrent verification did not start" }
                    emailVerificationStore.verify(fixture.token.tokenHash, verifiedAt)
                }
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(attempts.map { it.get(10, TimeUnit.SECONDS) }).containsExactlyInAnyOrder(true, false)
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM identity.users", String::class.java))
                .isEqualTo("active")
            assertThat(
                jdbcTemplate.queryForObject("SELECT consumed_at FROM identity.action_tokens", Instant::class.java),
            )
                .isEqualTo(verifiedAt)
        } finally {
            executor.shutdownNow()
        }
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
