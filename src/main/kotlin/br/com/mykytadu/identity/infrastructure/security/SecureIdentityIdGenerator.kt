package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.UserId
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

internal class SecureIdentityIdGenerator(private val clock: Clock, private val secureRandom: SecureRandom) :
    IdentityIdGenerator {

    override fun nextUserId(): UserId = UserId.from(nextUuidV7())

    override fun nextActionTokenId(): ActionTokenId = ActionTokenId.from(nextUuidV7())

    private fun nextUuidV7(): UUID {
        val timestamp = clock.millis() and TIMESTAMP_MASK
        val randomA = secureRandom.nextInt() and RANDOM_A_MASK
        val mostSignificantBits = (timestamp shl TIMESTAMP_SHIFT) or VERSION_SEVEN or randomA.toLong()
        val leastSignificantBits = (secureRandom.nextLong() and VARIANT_PAYLOAD_MASK) or RFC_4122_VARIANT
        return UUID(mostSignificantBits, leastSignificantBits)
    }

    companion object {
        private const val TIMESTAMP_MASK = 0xFFFFFFFFFFFFL
        private const val TIMESTAMP_SHIFT = 16
        private const val RANDOM_A_MASK = 0x0FFF
        private const val VERSION_SEVEN = 0x7000L
        private const val VARIANT_PAYLOAD_MASK = 0x3FFFFFFFFFFFFFFFL
        private const val RFC_4122_VARIANT = Long.MIN_VALUE
    }
}
