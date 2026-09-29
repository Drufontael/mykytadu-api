package br.com.mykytadu.identity.infrastructure

import br.com.mykytadu.identity.application.LoginApplicationProperties
import br.com.mykytadu.identity.application.SessionApplicationProperties
import br.com.mykytadu.identity.application.port.out.AuthenticationRateLimiter
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy
import br.com.mykytadu.identity.infrastructure.observability.MicrometerAuthenticationTelemetry
import br.com.mykytadu.identity.infrastructure.ratelimit.InMemoryAuthenticationRateLimiter
import br.com.mykytadu.identity.infrastructure.security.AccessTokenJwtDecoder
import br.com.mykytadu.identity.infrastructure.security.ConfiguredLoginOriginPolicy
import br.com.mykytadu.identity.infrastructure.security.HmacRefreshTokenDeriver
import br.com.mykytadu.identity.infrastructure.security.JwtAccessTokenService
import br.com.mykytadu.identity.infrastructure.security.JwtKeyMaterialFactory
import br.com.mykytadu.identity.infrastructure.security.RefreshDerivationKeyFactory
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.SecureRandom
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
    fun loginOriginPolicy(properties: IdentityRegistrationConfigurationProperties): LoginOriginPolicy =
        ConfiguredLoginOriginPolicy(properties.authentication.allowedWebOrigins)

    @Bean
    fun loginApplicationProperties(
        properties: IdentityRegistrationConfigurationProperties,
    ): LoginApplicationProperties = LoginApplicationProperties(
        accessTokenTtl = properties.authentication.accessTokenTtl,
        refreshTokenTtl = properties.authentication.refreshTokenTtl,
    )

    @Bean
    fun sessionApplicationProperties(
        properties: IdentityRegistrationConfigurationProperties,
    ): SessionApplicationProperties = SessionApplicationProperties(
        accessTokenTtl = properties.authentication.accessTokenTtl,
        refreshTokenTtl = properties.authentication.refreshTokenTtl,
        replayWindow = properties.authentication.refreshReplayWindow,
    )

    @Bean
    fun jwtAccessTokenService(
        properties: IdentityRegistrationConfigurationProperties,
        secureRandom: SecureRandom,
    ): JwtAccessTokenService = JwtAccessTokenService(
        keys = JwtKeyMaterialFactory.create(properties.jwt, secureRandom),
        issuer = properties.jwt.issuer,
        audience = properties.jwt.audience,
    )

    @Bean
    fun jwtDecoder(jwtAccessTokenService: JwtAccessTokenService, clock: Clock): JwtDecoder =
        AccessTokenJwtDecoder(jwtAccessTokenService, clock)

    @Bean
    fun authenticationTelemetry(registry: MeterRegistry): AuthenticationTelemetry =
        MicrometerAuthenticationTelemetry(registry)

    @Bean
    fun refreshTokenDeriver(
        properties: IdentityRegistrationConfigurationProperties,
        secureRandom: SecureRandom,
    ): HmacRefreshTokenDeriver = RefreshDerivationKeyFactory.create(properties.refreshDerivation, secureRandom)

    @Bean
    fun identityCorsConfigurationSource(
        properties: IdentityRegistrationConfigurationProperties,
    ): UrlBasedCorsConfigurationSource = UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration(
            "/api/v1/**",
            CorsConfiguration().apply {
                allowedOrigins = properties.authentication.allowedWebOrigins.toList()
                allowedMethods = listOf("GET", "POST")
                allowedHeaders = listOf("Content-Type", "Authorization", "X-CSRF-Token", "Idempotency-Key")
                allowCredentials = true
            },
        )
    }
}
