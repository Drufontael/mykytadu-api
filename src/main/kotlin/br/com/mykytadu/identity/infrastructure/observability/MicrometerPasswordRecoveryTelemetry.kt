package br.com.mykytadu.identity.infrastructure.observability

import br.com.mykytadu.identity.application.port.out.PasswordRecoveryTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

internal class MicrometerPasswordRecoveryTelemetry(registry: MeterRegistry) : PasswordRecoveryTelemetry {

    private val accepted = Counter.builder("mykytadu.identity.password.recovery.accepted")
        .description("Password recovery requests accepted uniformly")
        .register(registry)

    private val rateLimited = Counter.builder("mykytadu.identity.password.recovery.rate.limited")
        .description("Password recovery requests rate limited")
        .register(registry)

    private val deliveryUnavailable = Counter.builder("mykytadu.identity.password.recovery.delivery.unavailable")
        .description("Password reset email deliveries that were unavailable")
        .register(registry)

    private val processingFailed = Counter.builder("mykytadu.identity.password.recovery.processing.failed")
        .description("Password recovery attempts that failed during processing")
        .register(registry)

    override fun forgotPasswordAccepted() = accepted.increment()

    override fun forgotPasswordRateLimited() = rateLimited.increment()

    override fun forgotPasswordDeliveryUnavailable() = deliveryUnavailable.increment()

    override fun forgotPasswordProcessingFailed() = processingFailed.increment()
}
