package br.com.mykytadu.identity.infrastructure

import br.com.mykytadu.identity.application.RegistrationApplicationProperties
import br.com.mykytadu.identity.application.port.out.RegistrationRateLimiter
import br.com.mykytadu.identity.application.port.out.RegistrationTelemetry
import br.com.mykytadu.identity.application.port.out.VerificationEmailSender
import br.com.mykytadu.identity.infrastructure.mail.ControlledVerificationEmailSender
import br.com.mykytadu.identity.infrastructure.mail.EmailDeliveryMode
import br.com.mykytadu.identity.infrastructure.observability.MicrometerRegistrationTelemetry
import br.com.mykytadu.identity.infrastructure.ratelimit.InMemoryRegistrationRateLimiter
import br.com.mykytadu.identity.infrastructure.security.Argon2PasswordHasher
import br.com.mykytadu.identity.infrastructure.security.SecureActionTokenCryptography
import br.com.mykytadu.identity.infrastructure.security.SecureIdentityIdGenerator
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityRegistrationConfigurationProperties::class)
internal class IdentityRegistrationConfiguration {

    @Bean
    fun identityClock(): Clock = Clock.systemUTC()

    @Bean
    fun identitySecureRandom(): SecureRandom = SecureRandom()

    @Bean
    fun identityIdGenerator(clock: Clock, secureRandom: SecureRandom): SecureIdentityIdGenerator =
        SecureIdentityIdGenerator(clock, secureRandom)

    @Bean
    fun actionTokenCryptography(secureRandom: SecureRandom): SecureActionTokenCryptography =
        SecureActionTokenCryptography(secureRandom)

    @Bean
    fun argon2PasswordHasher(): Argon2PasswordHasher =
        Argon2PasswordHasher(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8())

    @Bean
    fun verificationEmailSender(properties: IdentityRegistrationConfigurationProperties): VerificationEmailSender =
        ControlledVerificationEmailSender(properties.email.deliveryMode)

    @Bean
    fun registrationRateLimiter(
        clock: Clock,
        properties: IdentityRegistrationConfigurationProperties,
    ): RegistrationRateLimiter = InMemoryRegistrationRateLimiter(
        clock = clock,
        registrationLimit = properties.registration.registrationLimit,
        registrationWindow = properties.registration.registrationWindow,
        resendLimit = properties.registration.resendLimit,
        resendWindow = properties.registration.resendWindow,
        resendCooldown = properties.registration.resendCooldown,
    )

    @Bean
    fun registrationApplicationProperties(
        properties: IdentityRegistrationConfigurationProperties,
    ): RegistrationApplicationProperties = RegistrationApplicationProperties(
        verificationTokenTtl = properties.registration.verificationTokenTtl,
        deliveryRetryAfter = properties.registration.deliveryRetryAfter,
    )

    @Bean
    fun registrationTelemetry(registry: MeterRegistry): RegistrationTelemetry =
        MicrometerRegistrationTelemetry(registry)
}

@ConfigurationProperties("mykytadu.identity")
data class IdentityRegistrationConfigurationProperties(
    val registration: RegistrationPolicyProperties = RegistrationPolicyProperties(),
    val authentication: AuthenticationPolicyProperties = AuthenticationPolicyProperties(),
    val email: EmailProperties = EmailProperties(),
)

data class RegistrationPolicyProperties(
    val verificationTokenTtl: Duration = Duration.ofHours(24),
    val deliveryRetryAfter: Duration = Duration.ofMinutes(1),
    val registrationLimit: Int = 10,
    val registrationWindow: Duration = Duration.ofMinutes(10),
    val resendLimit: Int = 5,
    val resendWindow: Duration = Duration.ofHours(1),
    val resendCooldown: Duration = Duration.ofMinutes(1),
)

data class AuthenticationPolicyProperties(
    val loginLimit: Int = 10,
    val loginWindow: Duration = Duration.ofMinutes(10),
)

data class EmailProperties(val deliveryMode: EmailDeliveryMode = EmailDeliveryMode.UNAVAILABLE)
