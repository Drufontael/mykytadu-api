package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.TokenHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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

    private fun limiter(): InMemoryRegistrationRateLimiter = InMemoryRegistrationRateLimiter(
        clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC),
        registrationLimit = 2,
        registrationWindow = Duration.ofMinutes(10),
        resendLimit = 2,
        resendWindow = Duration.ofHours(1),
        resendCooldown = Duration.ofMinutes(1),
    )
}
