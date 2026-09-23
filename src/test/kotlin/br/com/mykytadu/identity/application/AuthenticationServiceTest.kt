package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.AuthenticationOutcome
import br.com.mykytadu.identity.application.port.out.AuthenticationRateLimiter
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.application.port.out.PasswordVerifier
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class AuthenticationServiceTest {

    @ParameterizedTest
    @EnumSource(AuthenticationClient::class)
    fun `authenticates an active account for every approved client mode`(client: AuthenticationClient) {
        val telemetry = RecordingTelemetry()
        val service = service(account = account(UserStatus.ACTIVE), telemetry = telemetry)

        val outcome = service.authenticate(command(client))

        assertThat(outcome).isInstanceOf(AuthenticationOutcome.Authenticated::class.java)
        val authenticated = outcome as AuthenticationOutcome.Authenticated
        assertThat(authenticated.client).isEqualTo(client)
        assertThat(authenticated.principal.id).isEqualTo(USER_ID.value)
        assertThat(authenticated.principal.email).isEqualTo("Person@Example.COM")
        assertThat(authenticated.principal.roles).containsExactly("USER")
        assertThat(telemetry.results).containsExactly("accepted")
    }

    @Test
    fun `requires email verification only after the correct password`() {
        val telemetry = RecordingTelemetry()
        val outcome = service(account = account(UserStatus.PENDING), telemetry = telemetry)
            .authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.EmailVerificationRequired)
        assertThat(telemetry.results).containsExactly("email_verification_required")
    }

    @ParameterizedTest
    @ValueSource(strings = ["blocked", "deleted"])
    fun `returns generic invalid credentials for inactive accounts`(status: String) {
        val outcome = service(account = account(UserStatus.fromPersistenceValue(status)))
            .authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.InvalidCredentials)
    }

    @Test
    fun `returns generic invalid credentials for a wrong password`() {
        val verifier = RecordingPasswordVerifier(matches = false)
        val outcome = service(account = account(UserStatus.PENDING), verifier = verifier)
            .authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.InvalidCredentials)
        assertThat(verifier.expectedHash).isEqualTo(PASSWORD_HASH)
    }

    @Test
    fun `performs dummy password verification for an unknown email`() {
        val verifier = RecordingPasswordVerifier(matches = false)
        val outcome = service(account = null, verifier = verifier).authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.InvalidCredentials)
        assertThat(verifier.expectedHash).isNull()
        assertThat(verifier.password).isEqualTo(PASSWORD)
    }

    @Test
    fun `rejects before account lookup when the origin is rate limited`() {
        val repository = FakeUserAccountRepository(account(UserStatus.ACTIVE))
        val telemetry = RecordingTelemetry()
        val service = AuthenticationService(
            accounts = repository,
            passwordVerifier = RecordingPasswordVerifier(),
            rateLimiter = FixedRateLimiter(RateLimitDecision.Rejected(42)),
            telemetry = telemetry,
        )

        val outcome = service.authenticate(command())

        assertThat(outcome).isEqualTo(AuthenticationOutcome.RateLimited(42))
        assertThat(repository.lookups).isZero()
        assertThat(telemetry.results).containsExactly("rate_limited")
    }

    @Test
    fun `maps approved client ids without using request metadata`() {
        assertThat(AuthenticationClient.fromClientId(null)).isEqualTo(AuthenticationClient.LEGACY_NATIVE)
        assertThat(AuthenticationClient.fromClientId("mykytadu-web")).isEqualTo(AuthenticationClient.WEB)
        assertThat(AuthenticationClient.fromClientId("mykytadu-android")).isEqualTo(AuthenticationClient.ANDROID)
        assertThat(AuthenticationClient.fromClientId("mykytadu-ios")).isEqualTo(AuthenticationClient.IOS)
        assertThat(AuthenticationClient.fromClientId("mykytadu-desktop")).isEqualTo(AuthenticationClient.DESKTOP)
        assertThat(AuthenticationClient.fromClientId("unsupported-client")).isNull()
        assertThat(AuthenticationClient.WEB.web).isTrue()
        assertThat(AuthenticationClient.entries.filterNot { it == AuthenticationClient.WEB })
            .allMatch { !it.web }
    }

    @Test
    fun `redacts credentials and request identity from diagnostic text`() {
        assertThat(command().toString())
            .doesNotContain("Person@Example.COM", PASSWORD, "origin-key")
            .contains("email=[REDACTED]", "password=[REDACTED]", "requestKey=[REDACTED]")
    }

    private fun service(
        account: UserAccount?,
        verifier: PasswordVerifier = RecordingPasswordVerifier(),
        telemetry: AuthenticationTelemetry = RecordingTelemetry(),
    ): AuthenticationService = AuthenticationService(
        accounts = FakeUserAccountRepository(account),
        passwordVerifier = verifier,
        rateLimiter = FixedRateLimiter(RateLimitDecision.Allowed),
        telemetry = telemetry,
    )

    private fun command(client: AuthenticationClient = AuthenticationClient.LEGACY_NATIVE) = AuthenticateCommand(
        email = " Person@Example.COM ",
        password = PASSWORD,
        client = client,
        requestKey = "origin-key",
    )

    private fun account(status: UserStatus): UserAccount {
        val verifiedAt = NOW.plusSeconds(60)
        val user = User.restore(
            id = USER_ID,
            email = Email.from("Person@Example.COM"),
            displayName = "Person",
            status = status,
            emailVerifiedAt = verifiedAt.takeUnless { status == UserStatus.PENDING },
            createdAt = NOW,
            updatedAt = verifiedAt,
        )
        return UserAccount.restore(
            user = user,
            credential = PasswordCredential.argon2id(USER_ID, PASSWORD_HASH, NOW),
            roles = setOf(RoleAssignment.defaultFor(USER_ID)),
        )
    }

    private class FakeUserAccountRepository(private val account: UserAccount?) : UserAccountRepository {
        var lookups = 0

        override fun save(account: UserAccount): UserAccount = account

        override fun findByEmail(email: Email): UserAccount? {
            lookups += 1
            return account
        }
    }

    private class RecordingPasswordVerifier(private val matches: Boolean = true) : PasswordVerifier {
        var password: CharSequence? = null
        var expectedHash: PasswordHash? = null

        override fun matches(password: CharSequence, expectedHash: PasswordHash?): Boolean {
            this.password = password
            this.expectedHash = expectedHash
            return matches
        }
    }

    private class FixedRateLimiter(private val decision: RateLimitDecision) : AuthenticationRateLimiter {
        override fun acquireLogin(requestKey: String): RateLimitDecision = decision
    }

    private class RecordingTelemetry : AuthenticationTelemetry {
        val results = mutableListOf<String>()

        override fun accepted() {
            results += "accepted"
        }

        override fun invalidCredentials() {
            results += "invalid_credentials"
        }

        override fun emailVerificationRequired() {
            results += "email_verification_required"
        }

        override fun rateLimited() {
            results += "rate_limited"
        }
    }

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001"))
        private val PASSWORD_HASH = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}fixture")
        private const val PASSWORD = "a-secure-test-password"
    }
}
