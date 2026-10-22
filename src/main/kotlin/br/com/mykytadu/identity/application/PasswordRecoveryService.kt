package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.IdentityPasswordRecovery
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
import br.com.mykytadu.identity.domain.model.Email
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
@Profile("!test")
internal class PasswordRecoveryService(
    private val store: PasswordRecoveryStore,
    private val idGenerator: IdentityIdGenerator,
    private val tokenCryptography: ActionTokenCryptography,
    private val passwordHasher: PasswordHasher,
    private val emailSender: PasswordResetEmailSender,
    private val rateLimiter: PasswordRecoveryRateLimiter,
    private val telemetry: PasswordRecoveryTelemetry,
    private val clock: Clock,
    private val properties: PasswordRecoveryApplicationProperties,
) : IdentityPasswordRecovery {

    override fun requestPasswordReset(command: RequestPasswordResetCommand): RequestPasswordResetOutcome {
        val email = Email.from(command.email)
        val emailKey = tokenCryptography.hash(email.normalized)
        rateLimiter.acquireForgotPassword(command.requestKey, emailKey).let { decision ->
            if (decision is RateLimitDecision.Rejected) {
                telemetry.forgotPasswordRateLimited()
                return RequestPasswordResetOutcome.RateLimited(decision.retryAfterSeconds)
            }
        }

        val now = clock.instant()
        val tokenValue = tokenCryptography.generateToken()
        val token = PasswordResetTokenDraft(
            id = idGenerator.nextActionTokenId(),
            tokenHash = tokenCryptography.hash(tokenValue),
            createdAt = now,
            expiresAt = now.plus(properties.resetTokenTtl),
        )

        try {
            val recipient = store.createPasswordResetToken(email, token, now)
            if (recipient != null) {
                val delivery = runCatching { emailSender.send(recipient, tokenValue) }
                    .getOrDefault(DeliveryStatus.UNAVAILABLE)
                if (delivery == DeliveryStatus.UNAVAILABLE) {
                    telemetry.forgotPasswordDeliveryUnavailable()
                }
            }
            telemetry.forgotPasswordAccepted()
        } catch (_: RuntimeException) {
            telemetry.forgotPasswordProcessingFailed()
        }

        return RequestPasswordResetOutcome.Accepted
    }

    override fun resetPassword(command: ResetPasswordCommand): ResetPasswordOutcome {
        val now = clock.instant()
        val tokenHash = tokenCryptography.hash(command.actionToken)
        val passwordHash = passwordHasher.hash(command.newPassword)
        return try {
            if (store.resetPassword(tokenHash, passwordHash, now)) {
                telemetry.passwordResetCompleted()
                ResetPasswordOutcome.Reset
            } else {
                telemetry.passwordResetInvalidToken()
                ResetPasswordOutcome.InvalidToken
            }
        } catch (_: RuntimeException) {
            telemetry.passwordResetProcessingFailed()
            ResetPasswordOutcome.InvalidToken
        }
    }
}

data class PasswordRecoveryApplicationProperties(val resetTokenTtl: Duration)
