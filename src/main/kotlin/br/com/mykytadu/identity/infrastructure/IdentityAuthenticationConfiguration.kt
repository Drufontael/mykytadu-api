package br.com.mykytadu.identity.infrastructure

import br.com.mykytadu.identity.application.port.out.AuthenticationRateLimiter
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.infrastructure.observability.MicrometerAuthenticationTelemetry
import br.com.mykytadu.identity.infrastructure.ratelimit.InMemoryAuthenticationRateLimiter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
internal class IdentityAuthenticationConfiguration {

    @Bean
    fun authenticationRateLimiter(
        clock: Clock,
        properties: IdentityRegistrationConfigurationProperties,
    ): AuthenticationRateLimiter = InMemoryAuthenticationRateLimiter(
        clock = clock,
        limit = properties.authentication.loginLimit,
        window = properties.authentication.loginWindow,
    )

    @Bean
    fun authenticationTelemetry(registry: MeterRegistry): AuthenticationTelemetry =
        MicrometerAuthenticationTelemetry(registry)
}
