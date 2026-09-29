package br.com.mykytadu.identity.infrastructure.observability

import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

internal class MicrometerAuthenticationTelemetry(registry: MeterRegistry) : AuthenticationTelemetry {

    private val accepted = counter(registry, "accepted")
    private val invalidCredentials = counter(registry, "invalid_credentials")
    private val emailVerificationRequired = counter(registry, "email_verification_required")
    private val rateLimited = counter(registry, "rate_limited")
    private val refreshReuseDetected = counter(registry, "refresh_reuse_detected")
    private val refreshRotated = counter(registry, "refresh_rotated")
    private val refreshExpired = counter(registry, "refresh_expired")
    private val sessionRevoked = counter(registry, "session_revoked")

    override fun accepted() = accepted.increment()

    override fun invalidCredentials() = invalidCredentials.increment()

    override fun emailVerificationRequired() = emailVerificationRequired.increment()

    override fun rateLimited() = rateLimited.increment()

    override fun refreshReuseDetected() {
        record(refreshReuseDetected, "reuse_detected", "failure", "session_refresh_reuse_detected")
    }

    override fun refreshRotated() = record(refreshRotated, "rotated", "success", "session_refresh_rotated")

    override fun refreshExpired() = record(refreshExpired, "expired", "failure", "session_refresh_expired")

    override fun sessionRevoked() = record(sessionRevoked, "revoked", "success", "session_revoked")

    private fun record(counter: Counter, event: String, outcome: String, message: String) {
        counter.increment()
        val logEvent = if (outcome == "success") logger.atInfo() else logger.atWarn()
        logEvent
            .addKeyValue("module", "identity")
            .addKeyValue("event", event)
            .addKeyValue("category", "domain")
            .addKeyValue("outcome", outcome)
            .log(message)
    }

    private fun counter(registry: MeterRegistry, result: String): Counter =
        Counter.builder("mykytadu.identity.authentication.attempts")
            .description("Authentication and refresh security events by safe result category")
            .tag("result", result)
            .register(registry)

    companion object {
        private val logger = LoggerFactory.getLogger(MicrometerAuthenticationTelemetry::class.java)
    }
}
