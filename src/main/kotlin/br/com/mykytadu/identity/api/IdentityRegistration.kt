package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

@NamedInterface("api")
interface IdentityRegistration {

    fun register(command: RegisterAccountCommand): RegistrationOutcome

    fun resendVerification(command: ResendVerificationCommand): ResendVerificationOutcome

    fun verifyEmail(command: VerifyEmailCommand): EmailVerificationOutcome
}

@NamedInterface("api")
class RegisterAccountCommand(
    val email: String,
    val password: String,
    val displayName: String?,
    val requestKey: String,
) {

    override fun toString(): String = "RegisterAccountCommand(email=[REDACTED], password=[REDACTED])"
}

@NamedInterface("api")
class ResendVerificationCommand(val email: String, val requestKey: String) {

    override fun toString(): String = "ResendVerificationCommand(email=[REDACTED])"
}

@NamedInterface("api")
class VerifyEmailCommand(val actionToken: String) {

    override fun toString(): String = "VerifyEmailCommand(actionToken=[REDACTED])"
}

@NamedInterface("api")
sealed interface RegistrationOutcome {

    @NamedInterface("api")
    data class Created(val user: RegisteredUser) : RegistrationOutcome

    @NamedInterface("api")
    data object Rejected : RegistrationOutcome

    @NamedInterface("api")
    data class DeliveryUnavailable(val retryAfterSeconds: Long) : RegistrationOutcome

    @NamedInterface("api")
    data class RateLimited(val retryAfterSeconds: Long) : RegistrationOutcome
}

@NamedInterface("api")
sealed interface ResendVerificationOutcome {

    @NamedInterface("api")
    data object Accepted : ResendVerificationOutcome

    @NamedInterface("api")
    data class RateLimited(val retryAfterSeconds: Long) : ResendVerificationOutcome
}

@NamedInterface("api")
sealed interface EmailVerificationOutcome {

    @NamedInterface("api")
    data object Verified : EmailVerificationOutcome

    @NamedInterface("api")
    data object Invalid : EmailVerificationOutcome
}

@NamedInterface("api")
data class RegisteredUser(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val status: String,
    val roles: Set<String>,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    override fun toString(): String =
        "RegisteredUser(id=[REDACTED], email=[REDACTED], displayName=[REDACTED], status=$status, roles=$roles)"
}
