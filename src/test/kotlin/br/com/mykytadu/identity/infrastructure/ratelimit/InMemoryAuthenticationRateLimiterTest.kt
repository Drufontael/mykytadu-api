package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InMemoryAuthenticationRateLimiterTest {

    @Test
    fun `limits login attempts by opaque request key`() {
        val limiter = limiter()

        assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Rejected(600))
        assertThat(limiter.acquireLogin("another-origin-hash")).isEqualTo(RateLimitDecision.Allowed)
    }

    @Test
    fun `rejects invalid policy values`() {
        assertThatThrownBy { limiter(limit = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { limiter(window = Duration.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `resets the login limit exactly at the configured window`() {
        val clock = MutableTestClock(NOW)
        val limiter = limiter(clock = clock)

        repeat(2) { assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Allowed) }
        assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Rejected(600))

        clock.advance(Duration.ofMinutes(10))

        assertThat(limiter.acquireLogin("origin-hash")).isEqualTo(RateLimitDecision.Allowed)
    }

    private fun limiter(
        clock: java.time.Clock = MutableTestClock(NOW),
        limit: Int = 2,
        window: Duration = Duration.ofMinutes(10),
    ): InMemoryAuthenticationRateLimiter = InMemoryAuthenticationRateLimiter(
        clock = clock,
        limit = limit,
        window = window,
    )

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
    }
}
