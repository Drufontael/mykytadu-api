package br.com.mykytadu.api.security

import br.com.mykytadu.api.error.ApiProblemFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(SecurityFixtureController::class)
@Import(SecurityConfiguration::class, ProblemAuthenticationEntryPoint::class, ApiProblemFactory::class)
@ActiveProfiles("test")
class SecurityConfigurationTest(@Autowired private val mockMvc: MockMvc) {

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
}

@RestController
private class SecurityFixtureController {

    @GetMapping("/test/security/protected")
    fun protectedRoute() = mapOf("status" to "unexpected-public-response")
}
