package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import java.time.Instant
import java.util.UUID

interface AccessTokenIssuer {
    fun issue(
        principal: AuthenticatedPrincipal,
        client: AuthenticationClient,
        tokenId: UUID,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String
}

interface AccessTokenValidator {
    fun validate(token: String, validatedAt: Instant): AccessTokenClaims
}

data class AccessTokenClaims(
    val subject: UUID,
    val issuer: String,
    val audience: String,
    val tokenId: UUID,
    val roles: Set<String>,
    val clientId: String?,
    val issuedAt: Instant,
    val notBefore: Instant,
    val expiresAt: Instant,
    val keyId: String,
)

fun interface LoginOriginPolicy {
    fun isAllowed(origin: String?): Boolean
}
