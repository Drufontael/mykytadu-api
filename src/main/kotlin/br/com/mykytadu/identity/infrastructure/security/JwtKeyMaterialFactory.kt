package br.com.mykytadu.identity.infrastructure.security

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object JwtKeyMaterialFactory {

    fun create(properties: JwtConfigurationProperties, secureRandom: SecureRandom): JwtKeyRing =
        when (properties.keyMode) {
            JwtKeyMode.EPHEMERAL -> ephemeral(properties.activeKeyId, secureRandom)
            JwtKeyMode.CONFIGURED -> configured(properties)
        }.also { require(it.activeKeyId.isNotBlank()) { "JWT active key id must be configured" } }

    private fun ephemeral(keyId: String, secureRandom: SecureRandom): JwtKeyRing {
        val generator = KeyPairGenerator.getInstance("RSA").apply {
            initialize(RSA_KEY_SIZE, secureRandom)
        }
        val pair = generator.generateKeyPair()
        return JwtKeyRing(
            activeKeyId = keyId,
            activePrivateKey = pair.private as RSAPrivateKey,
            publicKeys = mapOf(keyId to pair.public as RSAPublicKey),
        )
    }

    private fun configured(properties: JwtConfigurationProperties): JwtKeyRing {
        require(properties.privateKeyPem.isNotBlank()) { "JWT private key must be configured" }
        require(properties.publicKeyPem.isNotBlank()) { "JWT public key must be configured" }
        val publicKeys = buildMap {
            put(properties.activeKeyId, parsePublicKey(properties.publicKeyPem))
            if (properties.previousKeyId != null || properties.previousPublicKeyPem != null) {
                put(
                    requireNotNull(properties.previousKeyId) { "Previous JWT key id must accompany its public key" },
                    parsePublicKey(
                        requireNotNull(properties.previousPublicKeyPem) {
                            "Previous JWT public key must accompany its key id"
                        },
                    ),
                )
            }
        }
        return JwtKeyRing(properties.activeKeyId, parsePrivateKey(properties.privateKeyPem), publicKeys)
    }

    private fun parsePrivateKey(pem: String): RSAPrivateKey = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(decodePem(pem, "PRIVATE KEY"))) as RSAPrivateKey

    private fun parsePublicKey(pem: String): RSAPublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(decodePem(pem, "PUBLIC KEY"))) as RSAPublicKey

    private fun decodePem(pem: String, label: String): ByteArray {
        val encoded = pem
            .replace("-----BEGIN $label-----", "")
            .replace("-----END $label-----", "")
            .filterNot(Char::isWhitespace)
        return Base64.getDecoder().decode(encoded)
    }

    private const val RSA_KEY_SIZE = 2048
}

enum class JwtKeyMode { EPHEMERAL, CONFIGURED }

data class JwtConfigurationProperties(
    val issuer: String = "https://api.mykytadu.com",
    val audience: String = "mykytadu-api",
    val activeKeyId: String = "local-ephemeral",
    val keyMode: JwtKeyMode = JwtKeyMode.CONFIGURED,
    val privateKeyPem: String = "",
    val publicKeyPem: String = "",
    val previousKeyId: String? = null,
    val previousPublicKeyPem: String? = null,
)
