package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.PasswordRecoveryRateLimiter
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.domain.model.TokenHash
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class InMemoryPasswordRecoveryRateLimiter(
    private val clock: Clock,
    private val forgotLimit: Int,
    private val forgotWindow: Duration,
    private val forgotCooldown: Duration,
) : PasswordRecoveryRateLimiter {

    private val forgotBuckets = mutableMapOf<String, Bucket>()
    private val forgotCooldowns = mutableMapOf<TokenHash, Instant>()

    @Synchronized
    override fun acquireForgotPassword(requestKey: String, emailKey: TokenHash): RateLimitDecision {
        val now = clock.instant()
        purgeExpired(now)
        val cooldownEndsAt = forgotCooldowns[emailKey]?.takeIf { it.isAfter(now) }
        return if (cooldownEndsAt != null) {
            RateLimitDecision.Rejected(secondsUntil(now, cooldownEndsAt))
        } else {
            acquireForgotOrigin(requestKey, emailKey, now)
        }
    }

    private fun acquireForgotOrigin(requestKey: String, emailKey: TokenHash, now: Instant): RateLimitDecision =
        when (val decision = acquireBucket(forgotBuckets, requestKey, forgotLimit, forgotWindow, now)) {
            is RateLimitDecision.Rejected -> decision

            RateLimitDecision.Allowed -> {
                forgotCooldowns[emailKey] = now.plus(forgotCooldown)
                RateLimitDecision.Allowed
            }
        }

    private fun acquireBucket(
        buckets: MutableMap<String, Bucket>,
        key: String,
        limit: Int,
        window: Duration,
        now: Instant,
    ): RateLimitDecision {
        val bucket = buckets.getOrPut(key) { Bucket(now, 0) }
        val windowEndsAt = bucket.startedAt.plus(window)
        if (!windowEndsAt.isAfter(now)) {
            bucket.startedAt = now
            bucket.count = 0
        }
        if (bucket.count >= limit) {
            return RateLimitDecision.Rejected(secondsUntil(now, bucket.startedAt.plus(window)))
        }
        bucket.count += 1
        return RateLimitDecision.Allowed
    }

    private fun purgeExpired(now: Instant) {
        forgotBuckets.entries.removeIf { !it.value.startedAt.plus(forgotWindow).isAfter(now) }
        forgotCooldowns.entries.removeIf { !it.value.isAfter(now) }
    }

    private fun secondsUntil(now: Instant, target: Instant): Long =
        Duration.between(now, target).seconds.coerceAtLeast(1)

    private data class Bucket(var startedAt: Instant, var count: Int)
}
