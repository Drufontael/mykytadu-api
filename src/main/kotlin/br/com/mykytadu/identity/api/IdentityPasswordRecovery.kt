package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
interface IdentityPasswordRecovery {

    fun requestPasswordReset(command: RequestPasswordResetCommand): RequestPasswordResetOutcome

    fun resetPassword(command: ResetPasswordCommand): ResetPasswordOutcome
}

@NamedInterface("api")
class RequestPasswordResetCommand(val email: String, val requestKey: String) {

    override fun toString(): String = "RequestPasswordResetCommand(email=[REDACTED])"
}

@NamedInterface("api")
class ResetPasswordCommand(val actionToken: String, val newPassword: CharSequence) {

    override fun toString(): String = "ResetPasswordCommand([REDACTED])"
}

@NamedInterface("api")
sealed interface RequestPasswordResetOutcome {

    @NamedInterface("api")
    data object Accepted : RequestPasswordResetOutcome

    @NamedInterface("api")
    data class RateLimited(val retryAfterSeconds: Long) : RequestPasswordResetOutcome
}

@NamedInterface("api")
sealed interface ResetPasswordOutcome {

    @NamedInterface("api")
    data object Reset : ResetPasswordOutcome

    @NamedInterface("api")
    data object InvalidToken : ResetPasswordOutcome
}
