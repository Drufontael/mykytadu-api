package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.domain.model.SessionId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

class IdentitySecurityAdaptersTest {

    @Test
    fun `generates UUIDv7 identifiers from the injected clock`() {
        val clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC)
        val generator = SecureIdentityIdGenerator(clock, SecureRandom())

        val userId = generator.nextUserId().value
        val actionTokenId = generator.nextActionTokenId().value
        val sessionId = generator.nextSessionId().value
        val familyId = generator.nextTokenFamilyId().value
        val accessTokenId = generator.nextAccessTokenId()

        assertThat(userId.version()).isEqualTo(7)
        assertThat(actionTokenId.version()).isEqualTo(7)
        assertThat(sessionId.version()).isEqualTo(7)
        assertThat(familyId.version()).isEqualTo(7)
        assertThat(accessTokenId.version()).isEqualTo(7)
        assertThat(userId.timestampBits()).isEqualTo(clock.millis())
        assertThat(actionTokenId.timestampBits()).isEqualTo(clock.millis())
        assertThat(sessionId.timestampBits()).isEqualTo(clock.millis())
        assertThat(familyId.timestampBits()).isEqualTo(clock.millis())
        assertThat(accessTokenId.timestampBits()).isEqualTo(clock.millis())
    }

    @Test
    fun `generates opaque tokens and deterministic SHA-256 hashes`() {
        val cryptography = SecureActionTokenCryptography(SecureRandom())

        val firstToken = cryptography.generateToken()
        val secondToken = cryptography.generateToken()

        assertThat(firstToken).hasSize(43).isNotEqualTo(secondToken)
        assertThat(cryptography.hash(firstToken)).isEqualTo(cryptography.hash(firstToken))
        assertThat(cryptography.hash(firstToken)).isNotEqualTo(cryptography.hash(secondToken))
        assertThat(cryptography.hash(firstToken).toString()).isEqualTo("[REDACTED]")
    }

    @Test
    fun `hashes passwords with versioned Argon2id parameters`() {
        val encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
        val hasher = Argon2PasswordHasher(encoder)

        val hash = hasher.hash("a-secure-test-password")

        assertThat(hash.toString()).isEqualTo("[REDACTED]")
        assertThat(encoder.matches("a-secure-test-password", hash.encodedValue())).isTrue()
        assertThat(hasher.matches("a-secure-test-password", hash)).isTrue()
        assertThat(hasher.matches("wrong-password", hash)).isFalse()
        assertThat(hasher.matches("a-secure-test-password", null)).isFalse()
        assertThat(hash.encodedValue()).startsWith("${'$'}argon2id${'$'}v=")
    }

    @Test
    fun `derives deterministic refresh tokens with the selected HMAC key`() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val deriver = RefreshDerivationKeyFactory.create(
            RefreshDerivationProperties(
                activeKeyId = "refresh-key-1",
                activeKeyBase64 = key,
            ),
            SecureRandom(),
        )
        val predecessor = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        val successor = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))

        val first = deriver.derive("refresh-key-1", predecessor, successor, "request-1")

        assertThat(first).hasSize(43)
        assertThat(deriver.derive("refresh-key-1", predecessor, successor, "request-1")).isEqualTo(first)
        assertThat(deriver.derive("refresh-key-1", predecessor, successor, "request-2")).isNotEqualTo(first)
        assertThat(deriver.derive("unknown", predecessor, successor, "request-1")).isNull()
    }

    private fun java.util.UUID.timestampBits(): Long = mostSignificantBits ushr 16
}
