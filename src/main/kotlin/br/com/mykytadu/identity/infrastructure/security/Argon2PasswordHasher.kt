package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.PasswordVerifier
import br.com.mykytadu.identity.domain.model.PasswordHash
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

internal class Argon2PasswordHasher(private val encoder: Argon2PasswordEncoder) :
    PasswordHasher,
    PasswordVerifier {

    private val dummyHash = PasswordHash.from(
        requireNotNull(encoder.encode(DUMMY_PASSWORD)) { "Argon2 encoder returned no dummy hash" },
    )

    override fun hash(password: CharSequence): PasswordHash = PasswordHash.from(
        requireNotNull(encoder.encode(password)) { "Argon2 encoder returned no hash" },
    )

    override fun matches(password: CharSequence, expectedHash: PasswordHash?): Boolean {
        val matches = encoder.matches(password, (expectedHash ?: dummyHash).encodedValue())
        return expectedHash != null && matches
    }

    companion object {
        private const val DUMMY_PASSWORD = "dummy-password-used-only-for-equalized-verification-work"
    }
}
