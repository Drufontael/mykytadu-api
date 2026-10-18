package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.RequestPasswordResetCommand
import br.com.mykytadu.identity.api.RequestPasswordResetOutcome
import br.com.mykytadu.identity.application.port.out.ActionTokenCryptography
import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryRateLimiter
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryStore
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryTelemetry
import br.com.mykytadu.identity.application.port.out.PasswordResetEmailSender
import br.com.mykytadu.identity.application.port.out.PasswordResetTokenDraft
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PasswordRecoveryServiceTest {

    @Test
    fun `persists reset token with 2 hours TTL and sends email for active user`() {
        val events = mutableListOf<String>()
        val store = FakePasswordRecoveryStore(events, recipient = "person@example.com")
        val sender = FakeResetEmailSender(events)
        val service = service(store = store, sender = sender)

        val outcome = service.requestPasswordReset(forgotCommand())

        assertThat(outcome).isEqualTo(RequestPasswordResetOutcome.Accepted)
        assertThat(events).containsExactly("store", "email")
        assertThat(store.createdToken).isNotNull
        assertThat(store.createdToken!!.expiresAt).isEqualTo(NOW.plus(Duration.ofHours(2)))
        assertThat(store.createdToken!!.tokenHash.persistenceValue()).isNotEqualTo(RAW_TOKEN)
        assertThat(sender.lastRecipient).isEqualTo("person@example.com")
        assertThat(sender.lastToken).isEqualTo(RAW_TOKEN)
    }

    @Test
    fun `returns accepted without sending email when account does not exist or is inactive`() {
        val events = mutableListOf<String>()
        val store = FakePasswordRecoveryStore(events, recipient = null)
        val sender = FakeResetEmailSender(events)
        val service = service(store = store, sender = sender)

        val outcome = service.requestPasswordReset(forgotCommand())

        assertThat(outcome).isEqualTo(RequestPasswordResetOutcome.Accepted)
        assertThat(events).containsExactly("store")
        assertThat(sender.lastToken).isNull()
    }

    @Test
    fun `returns accepted when email delivery fails`() {
        val events = mutableListOf<String>()
        val store = FakePasswordRecoveryStore(events, recipient = "person@example.com")
        val sender = FakeResetEmailSender(events, deliveryStatus = DeliveryStatus.UNAVAILABLE)
        val service = service(store = store, sender = sender)

        val outcome = service.requestPasswordReset(forgotCommand())

        assertThat(outcome).isEqualTo(RequestPasswordResetOutcome.Accepted)
        assertThat(events).containsExactly("store", "email")
    }

    @Test
    fun `returns accepted when store throws runtime exception`() {
        val store = object : PasswordRecoveryStore {
            override fun createPasswordResetToken(
                email: Email,
                token: PasswordResetTokenDraft,
                invalidatedAt: Instant,
            ): String? = throw IllegalStateException("DB unavailable")
        }
        val service = service(store = store)

        val outcome = service.requestPasswordReset(forgotCommand())

        assertThat(outcome).isEqualTo(RequestPasswordResetOutcome.Accepted)
    }

    @Test
    fun `returns rate limited when rate limiter rejects request`() {
        val rateLimiter = object : PasswordRecoveryRateLimiter {
            override fun acquireForgotPassword(requestKey: String, emailKey: TokenHash): RateLimitDecision =
                RateLimitDecision.Rejected(45)
        }
        val service = service(rateLimiter = rateLimiter)

        val outcome = service.requestPasswordReset(forgotCommand())

        assertThat(outcome).isEqualTo(RequestPasswordResetOutcome.RateLimited(45))
    }

    private fun service(
        store: PasswordRecoveryStore = FakePasswordRecoveryStore(mutableListOf()),
        sender: PasswordResetEmailSender = FakeResetEmailSender(mutableListOf()),
        rateLimiter: PasswordRecoveryRateLimiter = FakeRateLimiter(),
    ): PasswordRecoveryService = PasswordRecoveryService(
        store = store,
        idGenerator = FixedIdGenerator(),
        tokenCryptography = FixedTokenCryptography(),
        emailSender = sender,
        rateLimiter = rateLimiter,
        telemetry = NoOpPasswordRecoveryTelemetry,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        properties = PasswordRecoveryApplicationProperties(Duration.ofHours(2)),
    )

    private fun forgotCommand() = RequestPasswordResetCommand(
        email = "person@example.com",
        requestKey = "request-key-123",
    )

    private class FakePasswordRecoveryStore(
        private val events: MutableList<String>,
        private val recipient: String? = null,
    ) : PasswordRecoveryStore {
        var createdToken: PasswordResetTokenDraft? = null

        override fun createPasswordResetToken(
            email: Email,
            token: PasswordResetTokenDraft,
            invalidatedAt: Instant,
        ): String? {
            events += "store"
            createdToken = token
            return recipient
        }
    }

    private class FakeResetEmailSender(
        private val events: MutableList<String>,
        private val deliveryStatus: DeliveryStatus = DeliveryStatus.ACCEPTED,
    ) : PasswordResetEmailSender {
        var lastRecipient: String? = null
        var lastToken: String? = null

        override fun send(recipient: String, actionToken: String): DeliveryStatus {
            events += "email"
            lastRecipient = recipient
            lastToken = actionToken
            return deliveryStatus
        }
    }

    private class FakeRateLimiter : PasswordRecoveryRateLimiter {
        override fun acquireForgotPassword(requestKey: String, emailKey: TokenHash): RateLimitDecision =
            RateLimitDecision.Allowed
    }

    private class FixedIdGenerator : IdentityIdGenerator {
        override fun nextUserId(): UserId = UserId.from(UUID.fromString("0199204a-1200-7001-8000-000000000001"))
        override fun nextActionTokenId(): ActionTokenId =
            ActionTokenId.from(UUID.fromString("0199204a-1200-7001-8000-000000000002"))
    }

    private class FixedTokenCryptography : ActionTokenCryptography {
        override fun generateToken(): String = RAW_TOKEN

        override fun hash(token: String): TokenHash {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            return TokenHash.sha256(digest.joinToString("") { "%02x".format(it) })
        }
    }

    private object NoOpPasswordRecoveryTelemetry : PasswordRecoveryTelemetry {
        override fun forgotPasswordAccepted() = Unit
        override fun forgotPasswordRateLimited() = Unit
        override fun forgotPasswordDeliveryUnavailable() = Unit
        override fun forgotPasswordProcessingFailed() = Unit
    }

    companion object {
        private const val RAW_TOKEN = "raw-reset-token-value-12345678901234567890"
        private val NOW = Instant.parse("2026-10-18T10:00:00Z")
    }
}
