package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.UUID

class JwtAccessTokenServiceTest {

    @Test
    fun `issues and validates the approved claims and ttl`() {
        val service = service(keyRing("active"))
        val expiresAt = NOW.plusSeconds(600)

        val token = service.issue(PRINCIPAL, AuthenticationClient.ANDROID, TOKEN_ID, NOW, expiresAt)
        val claims = service.validate(token, NOW.plusSeconds(1))

        assertThat(claims.subject).isEqualTo(PRINCIPAL.id)
        assertThat(claims.issuer).isEqualTo(ISSUER)
        assertThat(claims.audience).isEqualTo(AUDIENCE)
        assertThat(claims.tokenId).isEqualTo(TOKEN_ID)
        assertThat(claims.roles).containsExactly("USER")
        assertThat(claims.clientId).isEqualTo("mykytadu-android")
        assertThat(claims.issuedAt).isEqualTo(NOW)
        assertThat(claims.notBefore).isEqualTo(NOW)
        assertThat(claims.expiresAt).isEqualTo(expiresAt)
        assertThat(claims.keyId).isEqualTo("active")
    }

    @Test
    fun `omits the client claim for legacy native login`() {
        val service = service(keyRing("active"))

        val token = service.issue(PRINCIPAL, AuthenticationClient.LEGACY_NATIVE, TOKEN_ID, NOW, NOW.plusSeconds(600))

        assertThat(service.validate(token, NOW).clientId).isNull()
    }

    @Test
    fun `rejects expiration issuer audience and unknown key`() {
        val ring = keyRing("active")
        val token = service(ring).issue(PRINCIPAL, AuthenticationClient.IOS, TOKEN_ID, NOW, NOW.plusSeconds(600))

        assertThatThrownBy { service(ring).validate(token, NOW.plusSeconds(600)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Access token is expired")
        assertThatThrownBy { JwtAccessTokenService(ring, "wrong-issuer", AUDIENCE).validate(token, NOW) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Access token issuer is invalid")
        assertThatThrownBy { JwtAccessTokenService(ring, ISSUER, "wrong-audience").validate(token, NOW) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Access token audience is invalid")
        assertThatThrownBy {
            service(keyRing("other")).validate(token, NOW)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Access token key id is unknown")
    }

    @Test
    fun `keeps the previous public key valid during signing key rotation`() {
        val previous = keyRing("previous")
        val current = keyRing("current")
        val rotatingRing = JwtKeyRing(
            activeKeyId = current.activeKeyId,
            activePrivateKey = current.activePrivateKey,
            publicKeys = previous.publicKeys + current.publicKeys,
        )
        val previousToken = service(previous).issue(
            PRINCIPAL,
            AuthenticationClient.DESKTOP,
            TOKEN_ID,
            NOW,
            NOW.plusSeconds(600),
        )
        val currentToken = service(rotatingRing).issue(
            PRINCIPAL,
            AuthenticationClient.DESKTOP,
            TOKEN_ID,
            NOW,
            NOW.plusSeconds(600),
        )

        assertThat(service(rotatingRing).validate(previousToken, NOW).keyId).isEqualTo("previous")
        assertThat(service(rotatingRing).validate(currentToken, NOW).keyId).isEqualTo("current")
    }

    private fun service(ring: JwtKeyRing) = JwtAccessTokenService(ring, ISSUER, AUDIENCE)

    private fun keyRing(keyId: String): JwtKeyRing {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return JwtKeyRing(
            keyId,
            pair.private as RSAPrivateKey,
            mapOf(keyId to pair.public as RSAPublicKey),
        )
    }

    companion object {
        private const val ISSUER = "https://api.mykytadu.test"
        private const val AUDIENCE = "mykytadu-api"
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val TOKEN_ID = UUID.fromString("019937b6-3600-7001-8000-000000000020")
        private val PRINCIPAL = AuthenticatedPrincipal(
            UUID.fromString("019937b6-3600-7001-8000-000000000001"),
            "person@example.com",
            "Person",
            setOf("USER"),
            NOW,
            NOW.minusSeconds(60),
            NOW,
        )
    }
}
