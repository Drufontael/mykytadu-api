package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.CsrfOutcome
import br.com.mykytadu.identity.api.RefreshOutcome
import br.com.mykytadu.identity.api.RefreshSessionCommand
import br.com.mykytadu.identity.api.ReissueCsrfCommand
import br.com.mykytadu.identity.application.port.out.AccessTokenIssuer
import br.com.mykytadu.identity.application.port.out.RefreshTokenDeriver
import br.com.mykytadu.identity.application.port.out.SessionIdGenerator
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SessionServiceTest {

    @Test
    fun `rotates once and replays the same native refresh and access tokens`() {
        val store = RecordingSessionStore(initialSession(AuthenticationClient.ANDROID, null))
        val service = service(store)
        val command = refreshCommand(web = false, csrfToken = null, origin = null)

        val first = service.refresh(command) as RefreshOutcome.Refreshed
        val replay = service.refresh(command) as RefreshOutcome.Refreshed

        assertThat(first.session.refreshToken).isEqualTo(replay.session.refreshToken)
        assertThat(first.session.accessToken).isEqualTo(replay.session.accessToken)
        assertThat(first.session.refreshToken).isNotEqualTo(RAW_REFRESH)
        assertThat(store.created).hasSize(1)
        assertThat(store.sessions[PREDECESSOR_ID]?.revokeReason).isEqualTo(Session.ROTATED_REASON)
        assertThat(store.sessions[SUCCESSOR_ID]?.parentSessionId).isEqualTo(PREDECESSOR_ID)
    }

    @Test
    fun `reissues csrf and requires allowed origin and matching token for Web refresh`() {
        val store = RecordingSessionStore(initialSession(AuthenticationClient.WEB, HASHED_CSRF))
        val service = service(store)

        val csrf = service.reissueCsrf(ReissueCsrfCommand(RAW_REFRESH)) as CsrfOutcome.Issued
        assertThat(csrf.csrfToken).isEqualTo(GENERATED_TOKEN)

        assertThat(service.refresh(refreshCommand(true, "wrong", ALLOWED_ORIGIN)))
            .isEqualTo(RefreshOutcome.CsrfInvalid)
        val refreshed = service.refresh(refreshCommand(true, GENERATED_TOKEN, ALLOWED_ORIGIN))
            as RefreshOutcome.Refreshed
        assertThat(refreshed.session.refreshToken).isEqualTo(DERIVED_REFRESH)
        assertThat(refreshed.session.csrfToken).isEqualTo(GENERATED_TOKEN)
    }

    private fun service(store: RecordingSessionStore) = SessionService(
        sessions = store,
        accounts = FixedAccountRepository(account()),
        ids = FixedIds(),
        tokens = FixedTokens(),
        refreshDeriver = FixedRefreshDeriver(),
        accessTokens = FixedAccessTokens(),
        originPolicy = { it == ALLOWED_ORIGIN },
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        properties = SessionApplicationProperties(
            accessTokenTtl = Duration.ofMinutes(10),
            refreshTokenTtl = Duration.ofDays(30),
            replayWindow = Duration.ofMinutes(2),
        ),
    )

    private fun refreshCommand(web: Boolean, csrfToken: String?, origin: String?) = RefreshSessionCommand(
        refreshToken = RAW_REFRESH,
        idempotencyKey = IDEMPOTENCY_KEY,
        csrfToken = csrfToken,
        origin = origin,
        web = web,
    )

    private fun initialSession(client: AuthenticationClient, csrfHash: TokenHash?) = Session.initial(
        id = PREDECESSOR_ID,
        userId = USER_ID,
        refreshTokenHash = HASHED_REFRESH,
        tokenFamilyId = FAMILY_ID,
        clientId = client.clientId,
        csrfTokenHash = csrfHash,
        createdAt = NOW.minusSeconds(60),
        expiresAt = NOW.plusSeconds(3_600),
    )

    private fun account(): UserAccount = UserAccount.restore(
        user = User.restore(
            id = USER_ID,
            email = Email.from("person@example.com"),
            displayName = "Person",
            status = UserStatus.ACTIVE,
            emailVerifiedAt = NOW.minusSeconds(120),
            createdAt = NOW.minusSeconds(180),
            updatedAt = NOW.minusSeconds(120),
        ),
        credential = PasswordCredential.argon2id(USER_ID, PasswordHash.from("hash"), NOW.minusSeconds(180)),
        roles = setOf(RoleAssignment.defaultFor(USER_ID)),
    )

    private class RecordingSessionStore(initial: Session) : SessionStore {
        val sessions = linkedMapOf(initial.id to initial)
        val created = mutableListOf<Session>()

        override fun create(session: Session) {
            created += session
            sessions[session.id] = session
        }

        override fun update(session: Session) {
            sessions[session.id] = session
        }

        override fun findById(sessionId: SessionId): Session? = sessions[sessionId]

        override fun findByRefreshTokenHashForUpdate(refreshTokenHash: TokenHash): Session? =
            sessions.values.firstOrNull { it.refreshTokenHash == refreshTokenHash }
    }

    private class FixedAccountRepository(private val account: UserAccount) : UserAccountRepository {
        override fun save(account: UserAccount): UserAccount = account
        override fun findByEmail(email: Email): UserAccount? = account
        override fun findById(userId: UserId): UserAccount? = account.takeIf { it.user.id == userId }
    }

    private class FixedTokens : SessionTokenCryptography {
        override fun generateToken(): String = GENERATED_TOKEN
        override fun hash(token: String): TokenHash = when (token) {
            RAW_REFRESH -> HASHED_REFRESH
            GENERATED_TOKEN -> HASHED_CSRF
            IDEMPOTENCY_KEY -> HASHED_IDEMPOTENCY_KEY
            DERIVED_REFRESH -> HASHED_DERIVED_REFRESH
            else -> TokenHash.sha256("f".repeat(64))
        }
    }

    private class FixedRefreshDeriver : RefreshTokenDeriver {
        override val activeKeyId = "refresh-key-1"

        override fun derive(
            keyId: String,
            predecessorId: SessionId,
            successorId: SessionId,
            idempotencyKey: String,
        ): String? = DERIVED_REFRESH.takeIf {
            keyId == activeKeyId && predecessorId == PREDECESSOR_ID && successorId == SUCCESSOR_ID &&
                idempotencyKey == IDEMPOTENCY_KEY
        }
    }

    private class FixedAccessTokens : AccessTokenIssuer {
        override fun issue(
            principal: br.com.mykytadu.identity.api.AuthenticatedPrincipal,
            client: AuthenticationClient,
            tokenId: UUID,
            issuedAt: Instant,
            expiresAt: Instant,
        ): String = "access:$tokenId:$issuedAt:$expiresAt"
    }

    private class FixedIds : SessionIdGenerator {
        override fun nextSessionId(): SessionId = SUCCESSOR_ID
        override fun nextTokenFamilyId(): TokenFamilyId = FAMILY_ID
        override fun nextAccessTokenId(): UUID = ACCESS_TOKEN_ID
    }

    companion object {
        private val NOW = Instant.parse("2026-09-27T16:00:00Z")
        private const val RAW_REFRESH = "current-refresh"
        private const val DERIVED_REFRESH = "derived-refresh"
        private const val GENERATED_TOKEN = "generated-token"
        private const val IDEMPOTENCY_KEY = "request-1"
        private const val ALLOWED_ORIGIN = "http://localhost:8080"
        private val USER_ID = UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001"))
        private val PREDECESSOR_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        private val SUCCESSOR_ID = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))
        private val FAMILY_ID = TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000012"))
        private val ACCESS_TOKEN_ID = UUID.fromString("019937b6-3600-7001-8000-000000000013")
        private val HASHED_REFRESH = TokenHash.sha256("a".repeat(64))
        private val HASHED_CSRF = TokenHash.sha256("b".repeat(64))
        private val HASHED_IDEMPOTENCY_KEY = TokenHash.sha256("c".repeat(64))
        private val HASHED_DERIVED_REFRESH = TokenHash.sha256("d".repeat(64))
    }
}
