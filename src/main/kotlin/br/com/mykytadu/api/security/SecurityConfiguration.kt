package br.com.mykytadu.api.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val rolesClaim = jwt.claims["roles"]
            val roles = when (rolesClaim) {
                is Collection<*> -> rolesClaim.mapNotNull { it?.toString() }
                is String -> listOf(rolesClaim)
                else -> emptyList()
            }
            roles
                .filter { it.isNotBlank() }
                .map { role -> SimpleGrantedAuthority("ROLE_${role.trim()}") }
        }
        return converter
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticationEntryPoint: ProblemAuthenticationEntryPoint,
        accessDeniedHandler: ProblemAccessDeniedHandler,
        jwtAuthenticationConverter: JwtAuthenticationConverter,
    ): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .oauth2ResourceServer {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.jwt { jwtConfigurer ->
                    jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/auth/csrf",
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/password/forgot",
                    "/api/v1/auth/password/reset",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/register",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/verify-email/resend",
                ).permitAll()
                it.anyRequest().authenticated()
            }

        return http.build()
    }
}
