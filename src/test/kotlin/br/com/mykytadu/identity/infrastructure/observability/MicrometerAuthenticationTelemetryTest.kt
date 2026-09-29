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
        telemetry.refreshReuseDetected()
        telemetry.refreshRotated()
        telemetry.refreshExpired()
        telemetry.sessionRevoked()

        val meters = registry.find("mykytadu.identity.authentication.attempts").counters()
        assertThat(meters.map { it.id.getTag("result") })
            .containsExactlyInAnyOrder(
                "accepted",
                "invalid_credentials",
                "email_verification_required",
                "rate_limited",
                "refresh_reuse_detected",
                "refresh_rotated",
                "refresh_expired",
                "session_revoked",
            )
        assertThat(meters).allMatch { it.count() == 1.0 }
        assertThat(meters.flatMap { it.id.tags }.map { it.key }.distinct()).containsExactly("result")
        assertThat(meters.flatMap { it.id.tags }.map { it.value })
            .containsOnly(
                "accepted",
                "invalid_credentials",
                "email_verification_required",
                "rate_limited",
                "refresh_reuse_detected",
                "refresh_rotated",
                "refresh_expired",
                "session_revoked",
            )
            .doesNotContain("traceId", "refresh-token", "csrf-token", "cookie", "origin", "authorization")
    }
}
