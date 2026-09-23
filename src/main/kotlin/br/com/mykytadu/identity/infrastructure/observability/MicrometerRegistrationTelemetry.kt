package br.com.mykytadu.identity.infrastructure.observability

import br.com.mykytadu.identity.application.port.out.RegistrationTelemetry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

internal class MicrometerRegistrationTelemetry(registry: MeterRegistry) : RegistrationTelemetry {

    private val initialDeliveryUnavailable = Counter.builder("mykytadu.identity.registration.delivery.unavailable")
        .description("Initial verification email deliveries that were unavailable")
        .register(registry)
    private val resendDeliveryUnavailable = Counter.builder(
        "mykytadu.identity.registration.resend.delivery.unavailable",
    )
        .description("Verification email resend deliveries that were unavailable")
        .register(registry)
    private val resendProcessingFailed = Counter.builder("mykytadu.identity.registration.resend.processing.failed")
        .description("Verification email resend attempts that failed before delivery")
        .register(registry)

    override fun initialDeliveryUnavailable() = initialDeliveryUnavailable.increment()

    override fun resendDeliveryUnavailable() = resendDeliveryUnavailable.increment()

    override fun resendProcessingFailed() = resendProcessingFailed.increment()
}
