package br.com.mykytadu.identity.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MicrometerAuthenticationTelemetryTest {

    @Test
    fun `records only bounded authentication result categories`() {
        val registry = SimpleMeterRegistry()
        val telemetry = MicrometerAuthenticationTelemetry(registry)

        telemetry.accepted()
        telemetry.invalidCredentials()
        telemetry.emailVerificationRequired()
        telemetry.rateLimited()

        val meters = registry.find("mykytadu.identity.authentication.attempts").counters()
        assertThat(meters.map { it.id.getTag("result") })
            .containsExactlyInAnyOrder(
                "accepted",
                "invalid_credentials",
                "email_verification_required",
                "rate_limited",
            )
        assertThat(meters).allMatch { it.count() == 1.0 }
        assertThat(meters.flatMap { it.id.tags }.map { it.value })
            .noneMatch { it.contains("@") }
    }
}
