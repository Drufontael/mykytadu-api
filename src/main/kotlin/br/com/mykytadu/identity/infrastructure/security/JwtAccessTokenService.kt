package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.application.port.out.AccessTokenClaims
import br.com.mykytadu.identity.application.port.out.AccessTokenIssuer
import br.com.mykytadu.identity.application.port.out.AccessTokenValidator
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID

internal class JwtAccessTokenService(
    private val keys: JwtKeyRing,
    private val issuer: String,
    private val audience: String,
) : AccessTokenIssuer,
    AccessTokenValidator {

    override fun issue(
        principal: AuthenticatedPrincipal,
        client: AuthenticationClient,
        tokenId: UUID,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(principal.id.toString())
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date.from(issuedAt))
            .notBeforeTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .jwtID(tokenId.toString())
            .claim(ROLES_CLAIM, principal.roles.toList())
            .apply { client.clientId?.let { claim(CLIENT_ID_CLAIM, it) } }
            .build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.activeKeyId).build(),
            claims,
        ).apply { sign(RSASSASigner(keys.activePrivateKey)) }.serialize()
    }

    override fun validate(token: String, validatedAt: Instant): AccessTokenClaims {
        val jwt = runCatching { SignedJWT.parse(token) }
            .getOrElse { throw IllegalArgumentException("Access token is malformed") }
        val keyId = requireNotNull(jwt.header.keyID) { "Access token has no key id" }
        val publicKey = requireNotNull(keys.publicKeys[keyId]) { "Access token key id is unknown" }
        require(jwt.verify(RSASSAVerifier(publicKey))) { "Access token signature is invalid" }
        val claims = jwt.jwtClaimsSet
        val issuedAt = requireNotNull(claims.issueTime).toInstant()
        val notBefore = requireNotNull(claims.notBeforeTime).toInstant()
        val expiresAt = requireNotNull(claims.expirationTime).toInstant()
        require(claims.issuer == issuer) { "Access token issuer is invalid" }
        require(claims.audience.contains(audience)) { "Access token audience is invalid" }
        require(!validatedAt.isBefore(notBefore)) { "Access token is not active" }
        require(validatedAt.isBefore(expiresAt)) { "Access token is expired" }
        return AccessTokenClaims(
            subject = UUID.fromString(claims.subject),
            issuer = claims.issuer,
            audience = audience,
            tokenId = UUID.fromString(claims.jwtid),
            roles = claims.getStringListClaim(ROLES_CLAIM).toSet(),
            clientId = claims.getStringClaim(CLIENT_ID_CLAIM),
            issuedAt = issuedAt,
            notBefore = notBefore,
            expiresAt = expiresAt,
            keyId = keyId,
        )
    }

    companion object {
        private const val ROLES_CLAIM = "roles"
        private const val CLIENT_ID_CLAIM = "client_id"
    }
}

internal data class JwtKeyRing(
    val activeKeyId: String,
    val activePrivateKey: RSAPrivateKey,
    val publicKeys: Map<String, RSAPublicKey>,
)
