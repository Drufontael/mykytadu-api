package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.TokenHash

interface PasswordRecoveryRateLimiter {

    fun acquireForgotPassword(requestKey: String, emailKey: TokenHash): RateLimitDecision
}
