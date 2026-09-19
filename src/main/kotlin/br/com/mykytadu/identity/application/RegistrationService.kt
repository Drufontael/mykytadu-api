package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.IdentityRegistration
import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegisteredUser
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
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
@Profile("!test")
internal class RegistrationService(
    private val store: RegistrationStore,
    private val idGenerator: IdentityIdGenerator,
    private val passwordHasher: PasswordHasher,
    private val tokenCryptography: ActionTokenCryptography,
    private val emailSender: VerificationEmailSender,
    private val rateLimiter: RegistrationRateLimiter,
    private val telemetry: RegistrationTelemetry,
    private val clock: Clock,
    private val properties: RegistrationApplicationProperties,
) : IdentityRegistration {

    override fun register(command: RegisterAccountCommand): RegistrationOutcome =
        when (val decision = rateLimiter.acquireRegistration(command.requestKey)) {
            RateLimitDecision.Allowed -> registerAllowed(command)
            is RateLimitDecision.Rejected -> RegistrationOutcome.RateLimited(decision.retryAfterSeconds)
        }

    private fun registerAllowed(command: RegisterAccountCommand): RegistrationOutcome {
        val email = Email.from(command.email)
        val now = clock.instant()
        val account = UserAccount.pending(
            id = idGenerator.nextUserId(),
            email = email,
            displayName = command.displayName,
            passwordHash = passwordHasher.hash(command.password),
            now = now,
        )
        val tokenValue = tokenCryptography.generateToken()
        val token = ActionToken.emailVerification(
            id = idGenerator.nextActionTokenId(),
            userId = account.user.id,
            tokenHash = tokenCryptography.hash(tokenValue),
            createdAt = now,
            expiresAt = now.plus(properties.verificationTokenTtl),
        )

        try {
            store.create(account, token)
        } catch (_: RegistrationConflictException) {
            return RegistrationOutcome.Rejected
        }

        val delivery = try {
            emailSender.send(account.user.email.address, tokenValue)
        } catch (_: RuntimeException) {
            DeliveryStatus.UNAVAILABLE
        }

        return if (delivery == DeliveryStatus.ACCEPTED) {
            RegistrationOutcome.Created(account.toRegisteredUser())
        } else {
            telemetry.initialDeliveryUnavailable()
            RegistrationOutcome.DeliveryUnavailable(properties.deliveryRetryAfter.seconds)
        }
    }

    override fun resendVerification(command: ResendVerificationCommand): ResendVerificationOutcome {
        val email = Email.from(command.email)
        val emailKey = tokenCryptography.hash(email.normalized)
        rateLimiter.acquireResend(command.requestKey, emailKey).let { decision ->
            if (decision is RateLimitDecision.Rejected) {
                return ResendVerificationOutcome.RateLimited(decision.retryAfterSeconds)
            }
        }

        val now = clock.instant()
        val tokenValue = tokenCryptography.generateToken()
        val token = VerificationTokenDraft(
            id = idGenerator.nextActionTokenId(),
            tokenHash = tokenCryptography.hash(tokenValue),
            createdAt = now,
            expiresAt = now.plus(properties.verificationTokenTtl),
        )

        try {
            val recipient = store.replaceVerificationToken(email, token, now)
            if (recipient != null) {
                val delivery = runCatching { emailSender.send(recipient, tokenValue) }
                    .getOrDefault(DeliveryStatus.UNAVAILABLE)
                if (delivery == DeliveryStatus.UNAVAILABLE) {
                    telemetry.resendDeliveryUnavailable()
                }
            }
        } catch (_: RuntimeException) {
            telemetry.resendProcessingFailed()
        }

        return ResendVerificationOutcome.Accepted
    }

    private fun UserAccount.toRegisteredUser(): RegisteredUser = RegisteredUser(
        id = user.id.value,
        email = user.email.address,
        displayName = user.displayName,
        status = user.status.persistenceValue,
        roles = roles.mapTo(linkedSetOf()) { it.role.name },
        emailVerifiedAt = user.emailVerifiedAt,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}

data class RegistrationApplicationProperties(val verificationTokenTtl: Duration, val deliveryRetryAfter: Duration)
