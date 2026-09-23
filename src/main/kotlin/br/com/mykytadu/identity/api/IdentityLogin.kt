package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
interface IdentityLogin {
    fun login(command: LoginCommand): LoginOutcome
}

@NamedInterface("api")
class LoginCommand(
    val email: String,
    val password: String,
    val client: AuthenticationClient,
    val requestKey: String,
    val origin: String?,
) {
    override fun toString(): String =
        "LoginCommand(email=[REDACTED], password=[REDACTED], client=$client, requestKey=[REDACTED], origin=[REDACTED])"
}

@NamedInterface("api")
sealed interface LoginOutcome {
    @NamedInterface("api")
    data class Created(val session: InitialSession) : LoginOutcome

    @NamedInterface("api")
    data object InvalidCredentials : LoginOutcome

    @NamedInterface("api")
    data object EmailVerificationRequired : LoginOutcome

    @NamedInterface("api")
    data object InvalidOrigin : LoginOutcome

    @NamedInterface("api")
    data class RateLimited(val retryAfterSeconds: Long) : LoginOutcome
}

@NamedInterface("api")
data class InitialSession(
    val accessToken: String,
    val expiresInSeconds: Long,
    val refreshExpiresInSeconds: Long,
    val refreshToken: String?,
    val csrfToken: String?,
    val principal: AuthenticatedPrincipal,
    val client: AuthenticationClient,
) {
    override fun toString(): String =
        "InitialSession(accessToken=[REDACTED], refreshToken=[REDACTED], csrfToken=[REDACTED], " +
            "principal=$principal, client=$client)"
}
