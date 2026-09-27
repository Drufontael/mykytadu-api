package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
interface IdentitySession {
    fun reissueCsrf(command: ReissueCsrfCommand): CsrfOutcome
    fun refresh(command: RefreshSessionCommand): RefreshOutcome
}

@NamedInterface("api")
class ReissueCsrfCommand(val refreshToken: String) {
    override fun toString(): String = "ReissueCsrfCommand(refreshToken=[REDACTED])"
}

@NamedInterface("api")
class RefreshSessionCommand(
    val refreshToken: String,
    val idempotencyKey: String,
    val csrfToken: String?,
    val origin: String?,
    val web: Boolean,
) {
    override fun toString(): String = "RefreshSessionCommand(refreshToken=[REDACTED], idempotencyKey=[REDACTED], " +
        "csrfToken=[REDACTED], origin=[REDACTED], web=$web)"
}

@NamedInterface("api")
sealed interface CsrfOutcome {
    @NamedInterface("api")
    data class Issued(val csrfToken: String) : CsrfOutcome

    @NamedInterface("api")
    data object SessionInvalid : CsrfOutcome
}

@NamedInterface("api")
sealed interface RefreshOutcome {
    @NamedInterface("api")
    data class Refreshed(val session: RefreshedSession) : RefreshOutcome

    @NamedInterface("api")
    data object SessionInvalid : RefreshOutcome

    @NamedInterface("api")
    data object CsrfInvalid : RefreshOutcome
}

@NamedInterface("api")
data class RefreshedSession(
    val accessToken: String,
    val expiresInSeconds: Long,
    val refreshExpiresInSeconds: Long,
    val refreshToken: String,
    val csrfToken: String?,
    val principal: AuthenticatedPrincipal,
    val client: AuthenticationClient,
) {
    override fun toString(): String =
        "RefreshedSession(accessToken=[REDACTED], refreshToken=[REDACTED], csrfToken=[REDACTED], " +
            "principal=$principal, client=$client)"
}
