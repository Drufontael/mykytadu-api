package br.com.mykytadu.integration

import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.CsrfOutcome
import br.com.mykytadu.identity.api.IdentityLogin
import br.com.mykytadu.identity.api.IdentitySession
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import br.com.mykytadu.identity.api.RefreshOutcome
import br.com.mykytadu.identity.api.RefreshSessionCommand
import br.com.mykytadu.identity.api.ReissueCsrfCommand
import br.com.mykytadu.identity.application.port.out.AccessTokenValidator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class LoginIntegrationTests(
    @Autowired private val login: IdentityLogin,
    @Autowired private val sessions: IdentitySession,
    @Autowired private val accounts: UserAccountRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val accessTokens: AccessTokenValidator,
    @Autowired private val clock: Clock,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun prepareActiveAccount() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
        val pending = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Person",
            passwordHasher.hash(PASSWORD),
            NOW,
        )
        accounts.save(pending.verifyEmail(NOW.plusSeconds(60)))
    }

    @Test
    fun `creates a native session with only the refresh hash and a valid access token`() {
        val outcome = login.login(command(AuthenticationClient.ANDROID)) as LoginOutcome.Created

        assertThat(outcome.session.refreshToken).isNotBlank()
        assertThat(outcome.session.csrfToken).isNull()
        assertThat(jdbcTemplate.queryForObject("SELECT refresh_token_hash FROM identity.sessions", String::class.java))
            .hasSize(64)
            .isNotEqualTo(outcome.session.refreshToken)
        assertThat(jdbcTemplate.queryForObject("SELECT csrf_token_hash FROM identity.sessions", String::class.java))
            .isNull()
        val claims = accessTokens.validate(outcome.session.accessToken, clock.instant())
        assertThat(claims.subject).isEqualTo(USER_ID.value)
        assertThat(claims.audience).isEqualTo("mykytadu-api")
        assertThat(claims.clientId).isEqualTo("mykytadu-android")
        assertThat(claims.expiresAt).isEqualTo(claims.issuedAt.plusSeconds(600))
    }

    @Test
    fun `creates a Web session with hashed csrf only for the allowed origin`() {
        val outcome = login.login(command(AuthenticationClient.WEB, "http://localhost:8080")) as LoginOutcome.Created

        assertThat(outcome.session.csrfToken).isNotBlank()
        assertThat(jdbcTemplate.queryForObject("SELECT client_id FROM identity.sessions", String::class.java))
            .isEqualTo("mykytadu-web")
        assertThat(jdbcTemplate.queryForObject("SELECT csrf_token_hash FROM identity.sessions", String::class.java))
            .hasSize(64)
            .isNotEqualTo(outcome.session.csrfToken)
    }

    @Test
    fun `rotates and idempotently replays a native session in PostgreSQL`() {
        val initial = (login.login(command(AuthenticationClient.ANDROID)) as LoginOutcome.Created).session
        val refreshCommand = RefreshSessionCommand(
            refreshToken = requireNotNull(initial.refreshToken),
            idempotencyKey = "integration-request-1",
            csrfToken = null,
            origin = null,
            web = false,
        )

        val first = sessions.refresh(refreshCommand) as RefreshOutcome.Refreshed
        val replay = sessions.refresh(refreshCommand) as RefreshOutcome.Refreshed

        assertThat(first.session.refreshToken).isEqualTo(replay.session.refreshToken)
        assertThat(first.session.accessToken).isEqualTo(replay.session.accessToken)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity.sessions", Int::class.java)).isEqualTo(2)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.sessions WHERE revoke_reason = 'rotated' AND replay_until IS NOT NULL",
                Int::class.java,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `rotates without idempotency header and revokes family on predecessor reuse`() {
        val initial = (login.login(command(AuthenticationClient.ANDROID)) as LoginOutcome.Created).session
        val refreshCommand = RefreshSessionCommand(
            refreshToken = requireNotNull(initial.refreshToken),
            idempotencyKey = null,
            csrfToken = null,
            origin = null,
            web = false,
        )

        val first = sessions.refresh(refreshCommand) as RefreshOutcome.Refreshed

        assertThat(first.session.refreshToken).isNotBlank().isNotEqualTo(initial.refreshToken)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT rotation_idempotency_key_hash FROM identity.sessions WHERE revoke_reason = 'rotated'",
                String::class.java,
            ),
        ).hasSize(64)
        assertThat(sessions.refresh(refreshCommand)).isEqualTo(RefreshOutcome.SessionInvalid)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoke_reason FROM identity.sessions WHERE parent_session_id IS NOT NULL",
                String::class.java,
            ),
        ).isEqualTo("reuse_detected")
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus::class, names = ["BLOCKED", "DELETED"])
    fun `inactive account cannot refresh and all renewable families are revoked`(status: UserStatus) {
        val first = (login.login(command(AuthenticationClient.ANDROID)) as LoginOutcome.Created).session
        val second = (login.login(command(AuthenticationClient.IOS)) as LoginOutcome.Created).session
        jdbcTemplate.update("UPDATE identity.users SET status = ? WHERE id = ?", status.persistenceValue, USER_ID.value)

        assertThat(
            sessions.refresh(RefreshSessionCommand(requireNotNull(first.refreshToken), "first", null, null, false)),
        )
            .isEqualTo(RefreshOutcome.SessionInvalid)
        assertThat(
            sessions.refresh(RefreshSessionCommand(requireNotNull(second.refreshToken), "second", null, null, false)),
        )
            .isEqualTo(RefreshOutcome.SessionInvalid)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity.sessions WHERE revoke_reason = 'account_inactive'",
                Int::class.java,
            ),
        ).isEqualTo(2)
    }

    @Test
    fun `blocked account cannot replay a rotated refresh`() {
        val initial = (login.login(command(AuthenticationClient.ANDROID)) as LoginOutcome.Created).session
        val command = RefreshSessionCommand(requireNotNull(initial.refreshToken), "same-key", null, null, false)
        sessions.refresh(command) as RefreshOutcome.Refreshed
        jdbcTemplate.update("UPDATE identity.users SET status = 'blocked' WHERE id = ?", USER_ID.value)

        assertThat(sessions.refresh(command)).isEqualTo(RefreshOutcome.SessionInvalid)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT revoke_reason FROM identity.sessions WHERE parent_session_id IS NOT NULL",
                String::class.java,
            ),
        ).isEqualTo("account_inactive")
    }

    @Test
    fun `reissues csrf and rotates the Web session with a fresh csrf`() {
        val initial = (
            login.login(command(AuthenticationClient.WEB, "http://localhost:8080")) as LoginOutcome.Created
            ).session
        val csrf = sessions.reissueCsrf(ReissueCsrfCommand(requireNotNull(initial.refreshToken))) as CsrfOutcome.Issued

        val refreshed = sessions.refresh(
            RefreshSessionCommand(
                refreshToken = initial.refreshToken,
                idempotencyKey = "integration-web-request-1",
                csrfToken = csrf.csrfToken,
                origin = "http://localhost:8080",
                web = true,
            ),
        ) as RefreshOutcome.Refreshed

        assertThat(refreshed.session.refreshToken).isNotEqualTo(initial.refreshToken)
        assertThat(refreshed.session.csrfToken).isNotBlank().isNotEqualTo(csrf.csrfToken)
    }

    private fun command(client: AuthenticationClient, origin: String? = null) = LoginCommand(
        email = "person@example.com",
        password = PASSWORD,
        client = client,
        requestKey = UUID.randomUUID().toString(),
        origin = origin,
    )

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001"))
        private const val PASSWORD = "a-secure-test-password"

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
