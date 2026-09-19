package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.TokenHash

interface RegistrationRateLimiter {

    fun acquireRegistration(requestKey: String): RateLimitDecision

    fun acquireResend(requestKey: String, emailKey: TokenHash): RateLimitDecision
}

sealed interface RateLimitDecision {

    data object Allowed : RateLimitDecision

    data class Rejected(val retryAfterSeconds: Long) : RateLimitDecision
}
