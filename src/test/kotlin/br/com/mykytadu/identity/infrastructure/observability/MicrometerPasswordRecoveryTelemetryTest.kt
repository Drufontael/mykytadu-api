package br.com.mykytadu.identity.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MicrometerPasswordRecoveryTelemetryTest {

    @Test
    fun `records recovery events without tags or sensitive dimensions`() {
        val registry = SimpleMeterRegistry()
        val telemetry = MicrometerPasswordRecoveryTelemetry(registry)

        telemetry.forgotPasswordAccepted()
        telemetry.forgotPasswordRateLimited()
        telemetry.forgotPasswordDeliveryUnavailable()
        telemetry.forgotPasswordProcessingFailed()

        val counters = registry.meters
        assertThat(counters.map { it.id.name })
            .containsExactlyInAnyOrder(
                "mykytadu.identity.password.recovery.accepted",
                "mykytadu.identity.password.recovery.rate.limited",
                "mykytadu.identity.password.recovery.delivery.unavailable",
                "mykytadu.identity.password.recovery.processing.failed",
            )
        assertThat(counters).allSatisfy { meter ->
            assertThat(meter.id.tags).isEmpty()
            assertThat(meter.measure().single().value).isEqualTo(1.0)
        }
    }
}
