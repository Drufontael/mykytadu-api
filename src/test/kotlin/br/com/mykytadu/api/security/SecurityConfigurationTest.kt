package br.com.mykytadu.api.security

import br.com.mykytadu.api.error.ApiProblemFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(SecurityFixtureController::class)
@Import(
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    ApiProblemFactory::class,
)
@ActiveProfiles("test")
class SecurityConfigurationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accessDeniedHandler: ProblemAccessDeniedHandler,
) {

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

@RestController
private class SecurityFixtureController {

    @GetMapping("/test/security/protected")
    fun protectedRoute() = mapOf("status" to "unexpected-public-response")

    @PostMapping("/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/verify-email/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun publicRegistrationSurface() = Unit
}
