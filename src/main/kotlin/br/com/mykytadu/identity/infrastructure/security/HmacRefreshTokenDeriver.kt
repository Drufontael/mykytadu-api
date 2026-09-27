package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.RefreshTokenDeriver
import br.com.mykytadu.identity.domain.model.SessionId
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class HmacRefreshTokenDeriver(override val activeKeyId: String, private val keys: Map<String, ByteArray>) :
    RefreshTokenDeriver {

    override fun derive(
        keyId: String,
        predecessorId: SessionId,
        successorId: SessionId,
        idempotencyKey: String,
    ): String? = keys[keyId]?.let { key ->
        val idempotencyBytes = idempotencyKey.toByteArray(Charsets.UTF_8)
        val input = ByteBuffer.allocate(DOMAIN.size + UUID_BYTES * 2 + Int.SIZE_BYTES + idempotencyBytes.size)
            .put(DOMAIN)
            .putLong(predecessorId.value.mostSignificantBits)
            .putLong(predecessorId.value.leastSignificantBits)
            .putLong(successorId.value.mostSignificantBits)
            .putLong(successorId.value.leastSignificantBits)
            .putInt(idempotencyBytes.size)
            .put(idempotencyBytes)
            .array()
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(key, HMAC_ALGORITHM))
        }
        Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input))
    }

    companion object {
        private val DOMAIN = "mykytadu-refresh-v1".toByteArray(Charsets.US_ASCII)
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val UUID_BYTES = 16
    }
}

internal object RefreshDerivationKeyFactory {
    fun create(properties: RefreshDerivationProperties, secureRandom: SecureRandom): HmacRefreshTokenDeriver {
        require(properties.activeKeyId.isNotBlank()) { "Refresh derivation active key id must be configured" }
        val keys = when (properties.keyMode) {
            RefreshDerivationKeyMode.EPHEMERAL -> mapOf(
                properties.activeKeyId to ByteArray(KEY_BYTES).also(secureRandom::nextBytes),
            )

            RefreshDerivationKeyMode.CONFIGURED -> configured(properties)
        }
        return HmacRefreshTokenDeriver(properties.activeKeyId, keys)
    }

    private fun configured(properties: RefreshDerivationProperties): Map<String, ByteArray> = buildMap {
        put(properties.activeKeyId, decode(properties.activeKeyBase64, "active"))
        if (!properties.previousKeyId.isNullOrBlank() || !properties.previousKeyBase64.isNullOrBlank()) {
            put(
                requireNotNull(properties.previousKeyId) { "Previous refresh key id must accompany its key" },
                decode(
                    requireNotNull(properties.previousKeyBase64) { "Previous refresh key must accompany its key id" },
                    "previous",
                ),
            )
        }
    }

    private fun decode(value: String, label: String): ByteArray {
        require(value.isNotBlank()) { "Refresh derivation $label key must be configured" }
        return runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException("Refresh derivation $label key must be Base64", it) }
            .also {
                require(it.size >= KEY_BYTES) { "Refresh derivation $label key must contain at least $KEY_BYTES bytes" }
            }
    }

    private const val KEY_BYTES = 32
}

enum class RefreshDerivationKeyMode { EPHEMERAL, CONFIGURED }

data class RefreshDerivationProperties(
    val activeKeyId: String = "",
    val keyMode: RefreshDerivationKeyMode = RefreshDerivationKeyMode.CONFIGURED,
    val activeKeyBase64: String = "",
    val previousKeyId: String? = null,
    val previousKeyBase64: String? = null,
)
