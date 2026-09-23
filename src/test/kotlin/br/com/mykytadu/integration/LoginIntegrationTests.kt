package br.com.mykytadu.integration

import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.IdentityLogin
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import br.com.mykytadu.identity.application.port.out.AccessTokenValidator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
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
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class LoginIntegrationTests(
    @Autowired private val login: IdentityLogin,
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
