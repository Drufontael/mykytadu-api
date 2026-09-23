package br.com.mykytadu.identity.infrastructure.observability

import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

internal class MicrometerAuthenticationTelemetry(registry: MeterRegistry) : AuthenticationTelemetry {

    private val accepted = counter(registry, "accepted")
    private val invalidCredentials = counter(registry, "invalid_credentials")
    private val emailVerificationRequired = counter(registry, "email_verification_required")
    private val rateLimited = counter(registry, "rate_limited")

    override fun accepted() = accepted.increment()

    override fun invalidCredentials() = invalidCredentials.increment()

    override fun emailVerificationRequired() = emailVerificationRequired.increment()

    override fun rateLimited() = rateLimited.increment()

    private fun counter(registry: MeterRegistry, result: String): Counter =
        Counter.builder("mykytadu.identity.authentication.attempts")
            .description("Authentication attempts by safe result category")
            .tag("result", result)
            .register(registry)
}
