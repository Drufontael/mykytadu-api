package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.domain.model.PasswordHash
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

internal class Argon2PasswordHasher(private val encoder: Argon2PasswordEncoder) : PasswordHasher {

    override fun hash(password: CharSequence): PasswordHash = PasswordHash.from(
        requireNotNull(encoder.encode(password)) { "Argon2 encoder returned no hash" },
    )
}
