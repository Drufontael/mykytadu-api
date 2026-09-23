package br.com.mykytadu.integration

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.AuthenticationOutcome
import br.com.mykytadu.identity.api.IdentityAuthentication
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
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
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class AuthenticationIntegrationTests(
    @Autowired private val authentication: IdentityAuthentication,
    @Autowired private val accounts: UserAccountRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.action_tokens, identity.roles, identity.password_credentials, identity.users",
        )
    }

    @ParameterizedTest
    @EnumSource(AuthenticationClient::class)
    fun `authenticates an active account using real Argon2 and PostgreSQL`(client: AuthenticationClient) {
        accounts.save(account(UserStatus.ACTIVE))

        val outcome = authentication.authenticate(command(client))

        assertThat(outcome).isInstanceOf(AuthenticationOutcome.Authenticated::class.java)
        val authenticated = outcome as AuthenticationOutcome.Authenticated
        assertThat(authenticated.client).isEqualTo(client)
        assertThat(authenticated.principal.id).isEqualTo(USER_ID.value)
        assertThat(authenticated.principal.roles).containsExactly("USER")
    }

    @Test
    fun `distinguishes pending only for a correct password`() {
        accounts.save(account(UserStatus.PENDING))

        val correct = authentication.authenticate(command())
        val wrong = authentication.authenticate(command(password = "wrong-password"))

        assertThat(correct).isEqualTo(AuthenticationOutcome.EmailVerificationRequired)
        assertThat(wrong).isEqualTo(AuthenticationOutcome.InvalidCredentials)
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus::class, names = ["BLOCKED", "DELETED"])
    fun `does not reveal an inactive account with valid credentials`(status: UserStatus) {
        accounts.save(account(status))

        val outcome = authentication.authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.InvalidCredentials)
    }

    @Test
    fun `returns the generic result for an unknown normalized email`() {
        val outcome = authentication.authenticate(command(email = "unknown@example.com"))

        assertThat(outcome).isEqualTo(AuthenticationOutcome.InvalidCredentials)
    }

    private fun account(status: UserStatus): UserAccount {
        val verifiedAt = CREATED_AT.plusSeconds(60)
        return UserAccount.restore(
            user = User.restore(
                id = USER_ID,
                email = Email.from("Person@Example.COM"),
                displayName = "Person",
                status = status,
                emailVerifiedAt = verifiedAt.takeUnless { status == UserStatus.PENDING },
                createdAt = CREATED_AT,
                updatedAt = verifiedAt,
            ),
            credential = PasswordCredential.argon2id(USER_ID, passwordHasher.hash(PASSWORD), CREATED_AT),
            roles = setOf(RoleAssignment.defaultFor(USER_ID)),
        )
    }

    private fun command(
        client: AuthenticationClient = AuthenticationClient.LEGACY_NATIVE,
        email: String = " person@example.com ",
        password: String = PASSWORD,
    ): AuthenticateCommand = AuthenticateCommand(
        email = email,
        password = password,
        client = client,
        requestKey = UUID.randomUUID().toString(),
    )

    companion object {

        private val CREATED_AT = Instant.parse("2026-09-22T12:00:00Z")
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        private const val PASSWORD = "a-secure-test-password"

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
