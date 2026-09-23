package br.com.mykytadu.identity.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MicrometerRegistrationTelemetryTest {

    @Test
    fun `records registration failures without tags or sensitive dimensions`() {
        val registry = SimpleMeterRegistry()
        val telemetry = MicrometerRegistrationTelemetry(registry)

        telemetry.initialDeliveryUnavailable()
        telemetry.resendDeliveryUnavailable()
        telemetry.resendProcessingFailed()

        val counters = registry.meters
        assertThat(counters.map { it.id.name })
            .containsExactlyInAnyOrder(
                "mykytadu.identity.registration.delivery.unavailable",
                "mykytadu.identity.registration.resend.delivery.unavailable",
                "mykytadu.identity.registration.resend.processing.failed",
            )
        assertThat(counters).allSatisfy { meter ->
            assertThat(meter.id.tags).isEmpty()
            assertThat(meter.measure().single().value).isEqualTo(1.0)
        }
    }
}
