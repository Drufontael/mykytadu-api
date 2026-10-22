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

    private val resetCompleted = Counter.builder("mykytadu.identity.password.reset.completed")
        .description("Password resets completed successfully")
        .register(registry)

    private val resetInvalidToken = Counter.builder("mykytadu.identity.password.reset.invalid.token")
        .description("Password resets rejected due to invalid or expired action token")
        .register(registry)

    private val resetProcessingFailed = Counter.builder("mykytadu.identity.password.reset.processing.failed")
        .description("Password resets that failed during processing")
        .register(registry)

    override fun forgotPasswordAccepted() = accepted.increment()

    override fun forgotPasswordRateLimited() = rateLimited.increment()

    override fun forgotPasswordDeliveryUnavailable() = deliveryUnavailable.increment()

    override fun forgotPasswordProcessingFailed() = processingFailed.increment()

    override fun passwordResetCompleted() = resetCompleted.increment()

    override fun passwordResetInvalidToken() = resetInvalidToken.increment()

    override fun passwordResetProcessingFailed() = resetProcessingFailed.increment()
}
