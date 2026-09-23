package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.AccessTokenValidator
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Clock

internal class AccessTokenJwtDecoder(private val validator: AccessTokenValidator, private val clock: Clock) :
    JwtDecoder {

    override fun decode(token: String): Jwt {
        val claims = try {
            validator.validate(token, clock.instant())
        } catch (_: IllegalArgumentException) {
            throw BadJwtException("Access token is invalid")
        }
        val claimSet = buildMap<String, Any> {
            put("sub", claims.subject.toString())
            put("iss", claims.issuer)
            put("aud", listOf(claims.audience))
            put("jti", claims.tokenId.toString())
            put("roles", claims.roles.toList())
            claims.clientId?.let { put("client_id", it) }
        }
        return Jwt(
            token,
            claims.issuedAt,
            claims.expiresAt,
            mapOf("alg" to "RS256", "kid" to claims.keyId),
            claimSet,
        )
    }
}
