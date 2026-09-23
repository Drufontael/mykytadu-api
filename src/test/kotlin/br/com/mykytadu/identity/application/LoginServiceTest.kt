package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.AuthenticationOutcome
import br.com.mykytadu.identity.api.IdentityAuthentication
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import br.com.mykytadu.identity.application.port.out.AccessTokenIssuer
import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy
import br.com.mykytadu.identity.application.port.out.SessionIdGenerator
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class LoginServiceTest {

    @Test
    fun `creates a native session returning refresh without csrf`() {
        val store = RecordingSessionStore()
        val outcome = service(store = store).login(command(AuthenticationClient.ANDROID))

        assertThat(outcome).isInstanceOf(LoginOutcome.Created::class.java)
        val created = (outcome as LoginOutcome.Created).session
        assertThat(created.accessToken).isEqualTo("signed-access-token")
        assertThat(created.expiresInSeconds).isEqualTo(600)
        assertThat(created.refreshToken).isEqualTo("refresh-token")
        assertThat(created.csrfToken).isNull()
        assertThat(store.session!!.clientId).isEqualTo("mykytadu-android")
        assertThat(store.session!!.csrfTokenHash).isNull()
        assertThat(store.session!!.expiresAt).isEqualTo(NOW.plus(Duration.ofDays(30)))
        assertThat(store.session!!.refreshTokenHash.toString()).isEqualTo("[REDACTED]")
    }

    @Test
    fun `creates a Web session only for an allowed origin`() {
        val store = RecordingSessionStore()
        val outcome = service(store = store).login(command(AuthenticationClient.WEB, "http://localhost:8080"))

        val created = (outcome as LoginOutcome.Created).session
        assertThat(created.refreshToken).isEqualTo("refresh-token")
        assertThat(created.csrfToken).isEqualTo("csrf-token")
        assertThat(store.session!!.clientId).isEqualTo("mykytadu-web")
        assertThat(store.session!!.csrfTokenHash).isNotNull
    }

    @Test
    fun `rejects a Web origin without creating credentials or session`() {
        val store = RecordingSessionStore()
        val tokens = FixedSessionTokens()
        val outcome = service(store = store, tokens = tokens)
            .login(command(AuthenticationClient.WEB, "https://attacker.example"))

        assertThat(outcome).isEqualTo(LoginOutcome.InvalidOrigin)
        assertThat(store.session).isNull()
        assertThat(tokens.generated).isZero()
    }

    @Test
    fun `propagates safe authentication failures without creating a session`() {
        val store = RecordingSessionStore()
        val outcomes = listOf(
            AuthenticationOutcome.InvalidCredentials to LoginOutcome.InvalidCredentials,
            AuthenticationOutcome.EmailVerificationRequired to LoginOutcome.EmailVerificationRequired,
            AuthenticationOutcome.RateLimited(25) to LoginOutcome.RateLimited(25),
        )

        outcomes.forEach { (authenticationOutcome, expected) ->
            val result = service(authenticationOutcome = authenticationOutcome, store = store).login(command())
            assertThat(result).isEqualTo(expected)
        }
        assertThat(store.session).isNull()
    }

    private fun service(
        authenticationOutcome: AuthenticationOutcome = AuthenticationOutcome.Authenticated(
            PRINCIPAL,
            AuthenticationClient.ANDROID,
        ),
        store: RecordingSessionStore = RecordingSessionStore(),
        tokens: FixedSessionTokens = FixedSessionTokens(),
    ) = LoginService(
        authentication = FixedAuthentication(authenticationOutcome),
        sessions = store,
        idGenerator = FixedSessionIds,
        tokenCryptography = tokens,
        accessTokenIssuer = FixedAccessTokenIssuer,
        originPolicy = LoginOriginPolicy { it == "http://localhost:8080" },
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        properties = LoginApplicationProperties(Duration.ofMinutes(10), Duration.ofDays(30)),
    )

    private fun command(client: AuthenticationClient = AuthenticationClient.ANDROID, origin: String? = null) =
        LoginCommand("person@example.com", "password", client, "origin-key", origin)

    private class FixedAuthentication(private val outcome: AuthenticationOutcome) : IdentityAuthentication {
        override fun authenticate(command: AuthenticateCommand): AuthenticationOutcome = when (outcome) {
            is AuthenticationOutcome.Authenticated -> outcome.copy(client = command.client)
            else -> outcome
        }
    }

    private class RecordingSessionStore : SessionStore {
        var session: Session? = null
        override fun create(session: Session) {
            this.session = session
        }
    }

    private class FixedSessionTokens : SessionTokenCryptography {
        var generated = 0
        override fun generateToken(): String = if (generated++ == 0) "refresh-token" else "csrf-token"
        override fun hash(token: String): TokenHash = TokenHash.sha256(
            if (token == "refresh-token") "a".repeat(64) else "b".repeat(64),
        )
    }

    private object FixedSessionIds : SessionIdGenerator {
        override fun nextSessionId() = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        override fun nextTokenFamilyId() = TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))
        override fun nextAccessTokenId(): UUID = UUID.fromString("019937b6-3600-7001-8000-000000000012")
    }

    private object FixedAccessTokenIssuer : AccessTokenIssuer {
        override fun issue(
            principal: AuthenticatedPrincipal,
            client: AuthenticationClient,
            tokenId: UUID,
            issuedAt: Instant,
            expiresAt: Instant,
        ): String = "signed-access-token"
    }

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val PRINCIPAL = AuthenticatedPrincipal(
            UUID.fromString("019937b6-3600-7001-8000-000000000001"),
            "person@example.com",
            "Person",
            setOf("USER"),
            NOW,
            NOW.minusSeconds(60),
            NOW,
        )
    }
}
