package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

@NamedInterface("api")
interface IdentityAuthentication {

    fun authenticate(command: AuthenticateCommand): AuthenticationOutcome
}

@NamedInterface("api")
class AuthenticateCommand(
    val email: String,
    val password: String,
    val client: AuthenticationClient,
    val requestKey: String,
) {

    override fun toString(): String =
        "AuthenticateCommand(email=[REDACTED], password=[REDACTED], client=$client, requestKey=[REDACTED])"
}

@NamedInterface("api")
enum class AuthenticationClient(val clientId: String?, val web: Boolean) {
    LEGACY_NATIVE(null, false),
    WEB("mykytadu-web", true),
    ANDROID("mykytadu-android", false),
    IOS("mykytadu-ios", false),
    DESKTOP("mykytadu-desktop", false),
    ;

    companion object {

        fun fromClientId(clientId: String?): AuthenticationClient? = entries.firstOrNull { it.clientId == clientId }
    }
}

@NamedInterface("api")
sealed interface AuthenticationOutcome {

    @NamedInterface("api")
    data class Authenticated(val principal: AuthenticatedPrincipal, val client: AuthenticationClient) :
        AuthenticationOutcome

    @NamedInterface("api")
    data object InvalidCredentials : AuthenticationOutcome

    @NamedInterface("api")
    data object EmailVerificationRequired : AuthenticationOutcome

    @NamedInterface("api")
    data class RateLimited(val retryAfterSeconds: Long) : AuthenticationOutcome
}

@NamedInterface("api")
data class AuthenticatedPrincipal(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val roles: Set<String>,
    val emailVerifiedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
