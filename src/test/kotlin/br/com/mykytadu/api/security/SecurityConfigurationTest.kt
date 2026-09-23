package br.com.mykytadu.api.security

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
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
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
}

internal class FixedTestJwtDecoder : JwtDecoder {
    override fun decode(token: String): Jwt {
        if (token != "valid-test-token") throw BadJwtException("invalid test token")
        return Jwt.withTokenValue(token)
            .header("alg", "RS256")
            .subject("019937b6-3600-7001-8000-000000000001")
            .issuedAt(Instant.parse("2026-09-22T12:00:00Z"))
            .expiresAt(Instant.parse("2026-09-22T12:10:00Z"))
            .build()
    }
}

@RestController
private class SecurityFixtureController {

    @GetMapping("/test/security/protected")
    fun protectedRoute() = mapOf("status" to "unexpected-public-response")

    @PostMapping(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/verify-email/resend",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun publicRegistrationSurface() = Unit
}
