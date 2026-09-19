package br.com.mykytadu.identity.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IdentitySecurityAdaptersTest {

    @Test
    fun `generates UUIDv7 identifiers from the injected clock`() {
        val clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC)
        val generator = SecureIdentityIdGenerator(clock, SecureRandom())

        val userId = generator.nextUserId().value
        val actionTokenId = generator.nextActionTokenId().value

        assertThat(userId.version()).isEqualTo(7)
        assertThat(actionTokenId.version()).isEqualTo(7)
        assertThat(userId.timestampBits()).isEqualTo(clock.millis())
        assertThat(actionTokenId.timestampBits()).isEqualTo(clock.millis())
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
        assertThat(hash.encodedValue()).startsWith("${'$'}argon2id${'$'}v=")
    }

    private fun java.util.UUID.timestampBits(): Long = mostSignificantBits ushr 16
}
