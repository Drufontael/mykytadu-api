package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.RequestPasswordResetCommand
import br.com.mykytadu.identity.api.RequestPasswordResetOutcome
import br.com.mykytadu.identity.api.ResetPasswordCommand
import br.com.mykytadu.identity.api.ResetPasswordOutcome
import br.com.mykytadu.identity.application.port.out.ActionTokenCryptography
import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryRateLimiter
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryStore
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryTelemetry
import br.com.mykytadu.identity.application.port.out.PasswordResetEmailSender
import br.com.mykytadu.identity.application.port.out.PasswordResetTokenDraft
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
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

            override fun resetPassword(tokenHash: TokenHash, passwordHash: PasswordHash, now: Instant): Boolean = false
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

    @Test
    fun `resets password and notifies telemetry on success`() {
        val events = mutableListOf<String>()
        val store = FakePasswordRecoveryStore(events, resetSuccess = true)
        val telemetry = RecordingPasswordRecoveryTelemetry()
        val service = service(store = store, telemetry = telemetry)

        val outcome = service.resetPassword(ResetPasswordCommand(RAW_TOKEN, "new-strong-password-123"))

        assertThat(outcome).isEqualTo(ResetPasswordOutcome.Reset)
        assertThat(events).containsExactly("reset")
        assertThat(store.lastResetTokenHash).isNotNull
        assertThat(store.lastResetPasswordHash?.encodedValue()).contains("new-strong-password-123")
        assertThat(telemetry.resetCompletedCalled).isTrue()
        assertThat(telemetry.resetInvalidTokenCalled).isFalse()
    }

    @Test
    fun `returns invalid token when store returns false`() {
        val events = mutableListOf<String>()
        val store = FakePasswordRecoveryStore(events, resetSuccess = false)
        val telemetry = RecordingPasswordRecoveryTelemetry()
        val service = service(store = store, telemetry = telemetry)

        val outcome = service.resetPassword(ResetPasswordCommand(RAW_TOKEN, "new-strong-password-123"))

        assertThat(outcome).isEqualTo(ResetPasswordOutcome.InvalidToken)
        assertThat(events).containsExactly("reset")
        assertThat(telemetry.resetCompletedCalled).isFalse()
        assertThat(telemetry.resetInvalidTokenCalled).isTrue()
    }

    @Test
    fun `returns invalid token and records failure when store throws exception`() {
        val store = object : PasswordRecoveryStore {
            override fun createPasswordResetToken(
                email: Email,
                token: PasswordResetTokenDraft,
                invalidatedAt: Instant,
            ): String? = null

            override fun resetPassword(
                tokenHash: TokenHash,
                passwordHash: br.com.mykytadu.identity.domain.model.PasswordHash,
                now: Instant,
            ): Boolean = throw IllegalStateException("Database connection broken")
        }
        val telemetry = RecordingPasswordRecoveryTelemetry()
        val service = service(store = store, telemetry = telemetry)

        val outcome = service.resetPassword(ResetPasswordCommand(RAW_TOKEN, "new-strong-password-123"))

        assertThat(outcome).isEqualTo(ResetPasswordOutcome.InvalidToken)
        assertThat(telemetry.resetProcessingFailedCalled).isTrue()
    }

    private fun service(
        store: PasswordRecoveryStore = FakePasswordRecoveryStore(mutableListOf()),
        sender: PasswordResetEmailSender = FakeResetEmailSender(mutableListOf()),
        rateLimiter: PasswordRecoveryRateLimiter = FakeRateLimiter(),
        passwordHasher: PasswordHasher = FakePasswordHasher(),
        telemetry: PasswordRecoveryTelemetry = NoOpPasswordRecoveryTelemetry,
    ): PasswordRecoveryService = PasswordRecoveryService(
        store = store,
        idGenerator = FixedIdGenerator(),
        tokenCryptography = FixedTokenCryptography(),
        passwordHasher = passwordHasher,
        emailSender = sender,
        rateLimiter = rateLimiter,
        telemetry = telemetry,
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
        private val resetSuccess: Boolean = true,
    ) : PasswordRecoveryStore {
        var createdToken: PasswordResetTokenDraft? = null
        var lastResetTokenHash: TokenHash? = null
        var lastResetPasswordHash: PasswordHash? = null

        override fun createPasswordResetToken(
            email: Email,
            token: PasswordResetTokenDraft,
            invalidatedAt: Instant,
        ): String? {
            events += "store"
            createdToken = token
            return recipient
        }

        override fun resetPassword(tokenHash: TokenHash, passwordHash: PasswordHash, now: Instant): Boolean {
            events += "reset"
            lastResetTokenHash = tokenHash
            lastResetPasswordHash = passwordHash
            return resetSuccess
        }
    }

    private class FakePasswordHasher : PasswordHasher {
        override fun hash(password: CharSequence): PasswordHash =
            PasswordHash.from("\$argon2id\$v=19\$m=65536,t=3,p=1\$hashed-$password")
    }

    private class RecordingPasswordRecoveryTelemetry : PasswordRecoveryTelemetry {
        var resetCompletedCalled = false
        var resetInvalidTokenCalled = false
        var resetProcessingFailedCalled = false

        override fun forgotPasswordAccepted() = Unit
        override fun forgotPasswordRateLimited() = Unit
        override fun forgotPasswordDeliveryUnavailable() = Unit
        override fun forgotPasswordProcessingFailed() = Unit
        override fun passwordResetCompleted() {
            resetCompletedCalled = true
        }

        override fun passwordResetInvalidToken() {
            resetInvalidTokenCalled = true
        }

        override fun passwordResetProcessingFailed() {
            resetProcessingFailedCalled = true
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
        override fun passwordResetCompleted() = Unit
        override fun passwordResetInvalidToken() = Unit
        override fun passwordResetProcessingFailed() = Unit
    }

    companion object {
        private const val RAW_TOKEN = "raw-reset-token-value-12345678901234567890"
        private val NOW = Instant.parse("2026-10-18T10:00:00Z")
    }
}
