package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.ActionTokenCryptography
import br.com.mykytadu.identity.domain.model.TokenHash
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal class SecureActionTokenCryptography(private val secureRandom: SecureRandom) : ActionTokenCryptography {

    override fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(token: String): TokenHash {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return TokenHash.sha256(
            digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) },
        )
    }

    companion object {
        private const val TOKEN_BYTES = 32
        private const val BYTE_MASK = 0xFF
    }
}
