package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.TokenHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InMemoryRegistrationRateLimiterTest {

    @Test
    fun `limits registration attempts within a deterministic window`() {
        val limiter = limiter()

        assertThat(limiter.acquireRegistration("origin")).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireRegistration("origin")).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireRegistration("origin"))
            .isEqualTo(RateLimitDecision.Rejected(600))
    }

    @Test
    fun `applies resend cooldown without retaining the email`() {
        val limiter = limiter()
        val emailKey = TokenHash.sha256("b".repeat(64))

        assertThat(limiter.acquireResend("origin", emailKey)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireResend("another-origin", emailKey))
            .isEqualTo(RateLimitDecision.Rejected(60))
    }

    @Test
    fun `resets registration and resend limits exactly at their windows`() {
        val clock = MutableTestClock(NOW)
        val limiter = limiter(clock)
        val firstEmail = TokenHash.sha256("a".repeat(64))
        val secondEmail = TokenHash.sha256("b".repeat(64))
        val thirdEmail = TokenHash.sha256("c".repeat(64))

        repeat(2) { assertThat(limiter.acquireRegistration("origin")).isEqualTo(RateLimitDecision.Allowed) }
        assertThat(limiter.acquireRegistration("origin")).isEqualTo(RateLimitDecision.Rejected(600))
        assertThat(limiter.acquireResend("origin", firstEmail)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireResend("origin", secondEmail)).isEqualTo(RateLimitDecision.Allowed)
        assertThat(limiter.acquireResend("origin", thirdEmail)).isEqualTo(RateLimitDecision.Rejected(3_600))

        clock.advance(Duration.ofMinutes(10))
        assertThat(limiter.acquireRegistration("origin")).isEqualTo(RateLimitDecision.Allowed)
        clock.advance(Duration.ofMinutes(50))
        assertThat(limiter.acquireResend("origin", thirdEmail)).isEqualTo(RateLimitDecision.Allowed)
    }

    private fun limiter(clock: java.time.Clock = MutableTestClock(NOW)): InMemoryRegistrationRateLimiter =
        InMemoryRegistrationRateLimiter(
            clock = clock,
            registrationLimit = 2,
            registrationWindow = Duration.ofMinutes(10),
            resendLimit = 2,
            resendWindow = Duration.ofHours(1),
            resendCooldown = Duration.ofMinutes(1),
        )

    companion object {
        private val NOW = Instant.parse("2026-09-19T12:00:00Z")
    }
}
