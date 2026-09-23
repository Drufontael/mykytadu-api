package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.AuthenticationRateLimiter
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class InMemoryAuthenticationRateLimiter(
    private val clock: Clock,
    private val limit: Int,
    private val window: Duration,
) : AuthenticationRateLimiter {

    private val buckets = mutableMapOf<String, Bucket>()

    init {
        require(limit > 0) { "Login rate limit must be positive" }
        require(!window.isZero && !window.isNegative) { "Login rate limit window must be positive" }
    }

    @Synchronized
    override fun acquireLogin(requestKey: String): RateLimitDecision {
        val now = clock.instant()
        buckets.entries.removeIf { !it.value.startedAt.plus(window).isAfter(now) }
        val bucket = buckets.getOrPut(requestKey) { Bucket(now, 0) }
        if (bucket.count >= limit) {
            return RateLimitDecision.Rejected(secondsUntil(now, bucket.startedAt.plus(window)))
        }
        bucket.count += 1
        return RateLimitDecision.Allowed
    }

    private fun secondsUntil(now: Instant, end: Instant): Long =
        Duration.between(now, end).seconds.coerceAtLeast(MINIMUM_RETRY_SECONDS)

    private data class Bucket(val startedAt: Instant, var count: Int)

    companion object {
        private const val MINIMUM_RETRY_SECONDS = 1L
    }
}
