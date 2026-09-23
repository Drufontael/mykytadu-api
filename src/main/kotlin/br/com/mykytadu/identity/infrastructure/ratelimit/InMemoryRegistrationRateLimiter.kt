package br.com.mykytadu.identity.infrastructure.ratelimit

import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.application.port.out.RegistrationRateLimiter
import br.com.mykytadu.identity.domain.model.TokenHash
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class InMemoryRegistrationRateLimiter(
    private val clock: Clock,
    private val registrationLimit: Int,
    private val registrationWindow: Duration,
    private val resendLimit: Int,
    private val resendWindow: Duration,
    private val resendCooldown: Duration,
) : RegistrationRateLimiter {

    private val registrationBuckets = mutableMapOf<String, Bucket>()
    private val resendBuckets = mutableMapOf<String, Bucket>()
    private val resendCooldowns = mutableMapOf<TokenHash, Instant>()

    @Synchronized
    override fun acquireRegistration(requestKey: String): RateLimitDecision {
        val now = clock.instant()
        purgeExpired(now)
        return acquireBucket(registrationBuckets, requestKey, registrationLimit, registrationWindow, now)
    }

    @Synchronized
    override fun acquireResend(requestKey: String, emailKey: TokenHash): RateLimitDecision {
        val now = clock.instant()
        purgeExpired(now)
        val cooldownEndsAt = resendCooldowns[emailKey]?.takeIf { it.isAfter(now) }
        return if (cooldownEndsAt != null) {
            RateLimitDecision.Rejected(secondsUntil(now, cooldownEndsAt))
        } else {
            acquireResendOrigin(requestKey, emailKey, now)
        }
    }

    private fun acquireResendOrigin(requestKey: String, emailKey: TokenHash, now: Instant): RateLimitDecision =
        when (val decision = acquireBucket(resendBuckets, requestKey, resendLimit, resendWindow, now)) {
            is RateLimitDecision.Rejected -> decision

            RateLimitDecision.Allowed -> {
                resendCooldowns[emailKey] = now.plus(resendCooldown)
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
        registrationBuckets.entries.removeIf { !it.value.startedAt.plus(registrationWindow).isAfter(now) }
        resendBuckets.entries.removeIf { !it.value.startedAt.plus(resendWindow).isAfter(now) }
        resendCooldowns.entries.removeIf { !it.value.isAfter(now) }
    }

    private fun secondsUntil(now: Instant, end: Instant): Long =
        Duration.between(now, end).seconds.coerceAtLeast(MINIMUM_RETRY_SECONDS)

    private data class Bucket(var startedAt: Instant, var count: Int)

    companion object {
        private const val MINIMUM_RETRY_SECONDS = 1L
    }
}
