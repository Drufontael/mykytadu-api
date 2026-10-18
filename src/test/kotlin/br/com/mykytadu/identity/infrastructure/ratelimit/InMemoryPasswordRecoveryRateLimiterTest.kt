package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.TokenHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InMemoryPasswordRecoveryRateLimiterTest {

    @Test
    fun `applies forgot password cooldown per email`() {
        val limiter = limiter()
        val emailKey = TokenHash.sha256("b".repeat(64))

        assertThat(limiter.acquireForgotPassword("origin-1", emailKey)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireForgotPassword("origin-2", emailKey))
            .isEqualTo(RateLimitDecision.Rejected(60))
    }

    @Test
    fun `limits forgot attempts per origin within a deterministic window`() {
        val clock = MutableTestClock(NOW)
        val limiter = limiter(clock)
        val email1 = TokenHash.sha256("a".repeat(64))
        val email2 = TokenHash.sha256("b".repeat(64))
        val email3 = TokenHash.sha256("c".repeat(64))

        assertThat(limiter.acquireForgotPassword("origin", email1)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireForgotPassword("origin", email2)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireForgotPassword("origin", email3)).isEqualTo(RateLimitDecision.Rejected(3_600))

        clock.advance(Duration.ofHours(1))
        assertThat(limiter.acquireForgotPassword("origin", email3)).isEqualTo(RateLimitDecision.Allowed)
    }

    private fun limiter(clock: java.time.Clock = MutableTestClock(NOW)): InMemoryPasswordRecoveryRateLimiter =
        InMemoryPasswordRecoveryRateLimiter(
            clock = clock,
            forgotLimit = 2,
            forgotWindow = Duration.ofHours(1),
            forgotCooldown = Duration.ofMinutes(1),
        )

    companion object {
        private val NOW = Instant.parse("2026-10-18T10:00:00Z")
    }
}
