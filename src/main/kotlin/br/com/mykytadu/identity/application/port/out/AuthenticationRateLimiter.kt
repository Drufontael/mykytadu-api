package br.com.mykytadu.identity.application.port.out

interface AuthenticationRateLimiter {

    fun acquireLogin(requestKey: String): RateLimitDecision
}
