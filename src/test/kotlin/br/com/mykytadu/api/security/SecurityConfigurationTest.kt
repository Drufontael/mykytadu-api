package br.com.mykytadu.api.security

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@WebMvcTest(SecurityFixtureController::class)
@Import(
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    ApiProblemFactory::class,
    ApiExceptionHandler::class,
    SecurityConfigurationTest.TestJwtConfiguration::class,
)
@ActiveProfiles("test")
class SecurityConfigurationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accessDeniedHandler: ProblemAccessDeniedHandler,
) {

    @TestConfiguration(proxyBeanMethods = false)
    class TestJwtConfiguration {
        @Bean
        fun jwtDecoder(): JwtDecoder = FixedTestJwtDecoder()
    }

    @Test
    fun `requires authentication for an unmatched route`() {
        mockMvc.get("/test/security/protected")
            .andExpect {
                status { isUnauthorized() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(401) }
                jsonPath("$.code") { value("authentication_required") }
                jsonPath("$.traceId") { isString() }
                jsonPath("$.errors") { isEmpty() }
                header { string("WWW-Authenticate", "Bearer") }
                header { doesNotExist("Set-Cookie") }
            }
    }

    @Test
    fun `accepts a bearer token validated by the JWT resource server`() {
        mockMvc.get("/test/security/protected") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-test-token")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `authorizes user role on user-secured route`() {
        mockMvc.get("/test/security/user-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-user-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("USER") }
        }
    }

    @Test
    fun `authorizes admin role on admin-secured route`() {
        mockMvc.get("/test/security/admin-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("ADMIN") }
        }
    }

    @Test
    fun `authorizes principal having both roles on both routes`() {
        mockMvc.get("/test/security/user-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-both-token")
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/test/security/admin-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-both-token")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `denies user role when accessing admin-secured route`() {
        mockMvc.get("/test/security/admin-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-user-token")
        }.andExpect {
            status { isForbidden() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(403) }
            jsonPath("$.code") { value("authorization_denied") }
            jsonPath("$.traceId") { isString() }
            header { string(HttpHeaders.CACHE_CONTROL, "no-store") }
        }
    }

    @Test
    fun `denies admin role when accessing user-secured route`() {
        mockMvc.get("/test/security/user-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token")
        }.andExpect {
            status { isForbidden() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(403) }
            jsonPath("$.code") { value("authorization_denied") }
            jsonPath("$.traceId") { isString() }
            header { string(HttpHeaders.CACHE_CONTROL, "no-store") }
        }
    }

    @Test
    fun `denies token without roles on role-secured routes`() {
        mockMvc.get("/test/security/user-only") {
            header(HttpHeaders.AUTHORIZATION, "Bearer valid-no-roles-token")
        }.andExpect {
            status { isForbidden() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(403) }
            jsonPath("$.code") { value("authorization_denied") }
            header { string(HttpHeaders.CACHE_CONTROL, "no-store") }
        }
    }

    @Test
    fun `denies unauthenticated request to role-secured route with 401`() {
        mockMvc.get("/test/security/user-only")
            .andExpect {
                status { isUnauthorized() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("authentication_required") }
            }
    }

    @Test
    fun `renders authorization denial as problem details`() {
        val request = MockHttpServletRequest().apply {
            method = "GET"
            requestURI = "/test/security/forbidden"
        }
        val response = MockHttpServletResponse()

        accessDeniedHandler.handle(request, response, AccessDeniedException("sensitive-detail"))

        assertThat(response.status).isEqualTo(403)
        assertThat(response.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(response.contentAsString)
            .contains("\"code\":\"authorization_denied\"")
            .doesNotContain("sensitive-detail")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health", "/v3/api-docs", "/swagger-ui/index.html"])
    fun `does not expose technical surfaces anonymously`(path: String) {
        mockMvc.get(path)
            .andExpect {
                status { isUnauthorized() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("authentication_required") }
            }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
        ],
    )
    fun `explicitly permits registration surfaces`(path: String) {
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `explicitly permits csrf reissuance`() {
        mockMvc.get("/api/v1/auth/csrf").andExpect { status { isNoContent() } }
    }
}

internal class FixedTestJwtDecoder : JwtDecoder {
    override fun decode(token: String): Jwt {
        val roles = when (token) {
            "valid-user-token" -> listOf("USER")
            "valid-admin-token" -> listOf("ADMIN")
            "valid-both-token" -> listOf("USER", "ADMIN")
            "valid-no-roles-token" -> emptyList()
            "valid-test-token" -> listOf("USER")
            else -> throw BadJwtException("invalid test token")
        }
        return Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("019937b6-3600-7001-8000-000000000001")
            .claim("roles", roles)
            .issuedAt(Instant.parse("2026-09-22T12:00:00Z"))
            .expiresAt(Instant.parse("2026-09-22T12:10:00Z"))
            .build()
    }
}

@RestController
@Profile("test")
private class SecurityFixtureController {

    @GetMapping("/test/security/protected")
    fun protectedRoute() = mapOf("status" to "unexpected-public-response")

    @GetMapping("/test/security/user-only")
    @PreAuthorize("hasRole('USER')")
    fun userOnlyRoute() = mapOf("role" to "USER")

    @GetMapping("/test/security/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    fun adminOnlyRoute() = mapOf("role" to "ADMIN")

    @PostMapping(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/register",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/verify-email/resend",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun publicRegistrationSurface() = Unit

    @GetMapping("/api/v1/auth/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun publicCsrfSurface() = Unit
}
