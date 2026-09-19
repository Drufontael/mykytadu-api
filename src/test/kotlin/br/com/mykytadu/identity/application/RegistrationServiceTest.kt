package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegistrationOutcome
import br.com.mykytadu.identity.api.ResendVerificationCommand
import br.com.mykytadu.identity.api.ResendVerificationOutcome
import br.com.mykytadu.identity.application.port.out.ActionTokenCryptography
import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.application.port.out.RegistrationConflictException
import br.com.mykytadu.identity.application.port.out.RegistrationRateLimiter
import br.com.mykytadu.identity.application.port.out.RegistrationStore
import br.com.mykytadu.identity.application.port.out.RegistrationTelemetry
import br.com.mykytadu.identity.application.port.out.VerificationEmailSender
import br.com.mykytadu.identity.application.port.out.VerificationTokenDraft
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RegistrationServiceTest {

    @Test
    fun `persists before sending and returns the pending user`() {
        val events = mutableListOf<String>()
        val store = FakeRegistrationStore(events)
        val sender = FakeEmailSender(events)
        val service = service(store = store, sender = sender)

        val outcome = service.register(registerCommand())

        assertThat(outcome).isInstanceOf(RegistrationOutcome.Created::class.java)
        val created = outcome as RegistrationOutcome.Created
        assertThat(created.user.status).isEqualTo("pending")
        assertThat(created.user.roles).containsExactly("USER")
        assertThat(created.user.emailVerifiedAt).isNull()
        assertThat(events).containsExactly("store", "email")
        assertThat(store.createdToken!!.expiresAt).isEqualTo(NOW.plus(Duration.ofHours(24)))
        assertThat(store.createdToken!!.tokenHash.persistenceValue()).isNotEqualTo(RAW_TOKEN)
        assertThat(sender.lastToken).isEqualTo(RAW_TOKEN)
    }

    @Test
    fun `keeps the account persisted when initial email delivery is unavailable`() {
        val events = mutableListOf<String>()
        val store = FakeRegistrationStore(events)
        val sender = FakeEmailSender(events, DeliveryStatus.UNAVAILABLE)

        val outcome = service(store = store, sender = sender).register(registerCommand())

        assertThat(outcome).isEqualTo(RegistrationOutcome.DeliveryUnavailable(60))
        assertThat(store.createdAccount).isNotNull
        assertThat(events).containsExactly("store", "email")
    }

    @Test
    fun `returns a generic rejection without sending for duplicate registration`() {
        val events = mutableListOf<String>()
        val store = FakeRegistrationStore(events, conflict = true)
        val sender = FakeEmailSender(events)

        val outcome = service(store = store, sender = sender).register(registerCommand())

        assertThat(outcome).isEqualTo(RegistrationOutcome.Rejected)
        assertThat(sender.lastToken).isNull()
    }

    @Test
    fun `resends only for an eligible account while keeping the response uniform`() {
        val eligibleStore = FakeRegistrationStore(mutableListOf(), resendRecipient = "Person@Example.COM")
        val eligibleSender = FakeEmailSender(mutableListOf())

        val eligibleOutcome = service(store = eligibleStore, sender = eligibleSender)
            .resendVerification(resendCommand())
        val ineligibleOutcome = service(
            store = FakeRegistrationStore(mutableListOf()),
            sender = FakeEmailSender(mutableListOf()),
        ).resendVerification(resendCommand())

        assertThat(eligibleOutcome).isEqualTo(ResendVerificationOutcome.Accepted)
        assertThat(ineligibleOutcome).isEqualTo(ResendVerificationOutcome.Accepted)
        assertThat(eligibleStore.replacementToken).isNotNull
        assertThat(eligibleSender.lastRecipient).isEqualTo("Person@Example.COM")
    }

    @Test
    fun `returns retry information when registration is rate limited`() {
        val limiter = FakeRateLimiter(registrationDecision = RateLimitDecision.Rejected(30))

        val outcome = service(rateLimiter = limiter).register(registerCommand())

        assertThat(outcome).isEqualTo(RegistrationOutcome.RateLimited(30))
    }

    private fun service(
        store: FakeRegistrationStore = FakeRegistrationStore(mutableListOf()),
        sender: FakeEmailSender = FakeEmailSender(mutableListOf()),
        rateLimiter: RegistrationRateLimiter = FakeRateLimiter(),
    ): RegistrationService = RegistrationService(
        store = store,
        idGenerator = FixedIdGenerator(),
        passwordHasher = object : PasswordHasher {
            override fun hash(password: CharSequence): PasswordHash = PASSWORD_HASH
        },
        tokenCryptography = FixedTokenCryptography(),
        emailSender = sender,
        rateLimiter = rateLimiter,
        telemetry = NoOpRegistrationTelemetry,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        properties = RegistrationApplicationProperties(Duration.ofHours(24), Duration.ofMinutes(1)),
    )

    private fun registerCommand() = RegisterAccountCommand(
        email = " Person@Example.COM ",
        password = "a-secure-test-password",
        displayName = "Person",
        requestKey = "origin-key",
    )

    private fun resendCommand() = ResendVerificationCommand("person@example.com", "origin-key")

    private class FakeRegistrationStore(
        private val events: MutableList<String>,
        private val conflict: Boolean = false,
        private val resendRecipient: String? = null,
    ) : RegistrationStore {
        var createdAccount: UserAccount? = null
        var createdToken: ActionToken? = null
        var replacementToken: VerificationTokenDraft? = null

        override fun create(account: UserAccount, verificationToken: ActionToken) {
            if (conflict) throw RegistrationConflictException(IllegalStateException())
            events += "store"
            createdAccount = account
            createdToken = verificationToken
        }

        override fun replaceVerificationToken(
            email: Email,
            token: VerificationTokenDraft,
            invalidatedAt: Instant,
        ): String? {
            replacementToken = token
            return resendRecipient
        }
    }

    private class FakeEmailSender(
        private val events: MutableList<String>,
        private val status: DeliveryStatus = DeliveryStatus.ACCEPTED,
    ) : VerificationEmailSender {
        var lastRecipient: String? = null
        var lastToken: String? = null

        override fun send(recipient: String, actionToken: String): DeliveryStatus {
            events += "email"
            lastRecipient = recipient
            lastToken = actionToken
            return status
        }
    }

    private class FixedIdGenerator : IdentityIdGenerator {
        override fun nextUserId(): UserId = USER_ID
        override fun nextActionTokenId(): ActionTokenId = TOKEN_ID
    }

    private class FixedTokenCryptography : ActionTokenCryptography {
        override fun generateToken(): String = RAW_TOKEN

        override fun hash(token: String): TokenHash {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            return TokenHash.sha256(digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        }
    }

    private class FakeRateLimiter(
        private val registrationDecision: RateLimitDecision = RateLimitDecision.Allowed,
        private val resendDecision: RateLimitDecision = RateLimitDecision.Allowed,
    ) : RegistrationRateLimiter {
        override fun acquireRegistration(requestKey: String): RateLimitDecision = registrationDecision
        override fun acquireResend(requestKey: String, emailKey: TokenHash): RateLimitDecision = resendDecision
    }

    private object NoOpRegistrationTelemetry : RegistrationTelemetry {
        override fun initialDeliveryUnavailable() = Unit
        override fun resendDeliveryUnavailable() = Unit
        override fun resendProcessingFailed() = Unit
    }

    companion object {
        private val NOW = Instant.parse("2026-09-19T12:00:00Z")
        private val USER_ID = UserId.from(UUID.fromString("0199204a-1200-7001-8000-000000000001"))
        private val TOKEN_ID = ActionTokenId.from(UUID.fromString("0199204a-1200-7001-8000-000000000002"))
        private val PASSWORD_HASH = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}fixture")
        private const val RAW_TOKEN = "raw-verification-token"
    }
}
