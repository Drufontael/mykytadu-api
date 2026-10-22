package br.com.mykytadu.identity.infrastructure

import br.com.mykytadu.identity.application.PasswordRecoveryApplicationProperties
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryRateLimiter
import br.com.mykytadu.identity.application.port.out.PasswordRecoveryTelemetry
import br.com.mykytadu.identity.application.port.out.PasswordResetEmailSender
import br.com.mykytadu.identity.infrastructure.mail.ControlledPasswordResetEmailSender
import br.com.mykytadu.identity.infrastructure.observability.MicrometerPasswordRecoveryTelemetry
import br.com.mykytadu.identity.infrastructure.ratelimit.InMemoryPasswordRecoveryRateLimiter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
internal class IdentityPasswordRecoveryConfiguration {

    @Bean
    fun passwordResetEmailSender(properties: IdentityRegistrationConfigurationProperties): PasswordResetEmailSender =
        ControlledPasswordResetEmailSender(properties.email.deliveryMode)

    @Bean
    fun passwordRecoveryRateLimiter(
        clock: Clock,
        properties: IdentityRegistrationConfigurationProperties,
    ): PasswordRecoveryRateLimiter = InMemoryPasswordRecoveryRateLimiter(
        clock = clock,
        forgotLimit = properties.recovery.forgotLimit,
        forgotWindow = properties.recovery.forgotWindow,
        forgotCooldown = properties.recovery.forgotCooldown,
    )

    @Bean
    fun passwordRecoveryApplicationProperties(
        properties: IdentityRegistrationConfigurationProperties,
    ): PasswordRecoveryApplicationProperties = PasswordRecoveryApplicationProperties(
        resetTokenTtl = properties.recovery.resetTokenTtl,
    )

    @Bean
    fun passwordRecoveryTelemetry(registry: MeterRegistry): PasswordRecoveryTelemetry =
        MicrometerPasswordRecoveryTelemetry(registry)
}
