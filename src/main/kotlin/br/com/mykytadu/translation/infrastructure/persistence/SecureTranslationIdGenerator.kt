package br.com.mykytadu.translation.infrastructure.persistence

import br.com.mykytadu.translation.application.port.TranslationIdGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

@Component
class SecureTranslationIdGenerator(private val clock: Clock, private val secureRandom: SecureRandom = SecureRandom()) :
    TranslationIdGenerator {

    override fun nextId(): UUID {
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
