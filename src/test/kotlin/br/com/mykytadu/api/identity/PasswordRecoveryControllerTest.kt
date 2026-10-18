package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.identity.api.IdentityPasswordRecovery
import br.com.mykytadu.identity.api.RequestPasswordResetCommand
import br.com.mykytadu.identity.api.RequestPasswordResetOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(PasswordRecoveryController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    RequestKeyFactory::class,
    PasswordRecoveryControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class PasswordRecoveryControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val recovery: StubIdentityPasswordRecovery,
) {

    @BeforeEach
    fun resetStub() {
        recovery.outcome = RequestPasswordResetOutcome.Accepted
        recovery.commands.clear()
    }

    @Test
    fun `returns uniform accepted response for password recovery request`() {
        val response = mockMvc.post("/api/v1/auth/password/forgot") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com"}"""
        }.andExpect {
            status { isAccepted() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.accepted") { value(true) }
        }.andReturn().response

        assertThat(response.contentAsString).doesNotContain("person@example.com", "token")
        assertThat(recovery.commands.single().requestKey).hasSize(64)
        assertThat(recovery.commands.single().email).isEqualTo("person@example.com")
    }

    @Test
    fun `rejects invalid email with validation problem`() {
        mockMvc.post("/api/v1/auth/password/forgot") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"not-a-valid-email"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.errors") { isNotEmpty() }
        }

        assertThat(recovery.commands).isEmpty()
    }

    @Test
    fun `returns rate limit problem when rate limit exceeded`() {
        recovery.outcome = RequestPasswordResetOutcome.RateLimited(60)

        mockMvc.post("/api/v1/auth/password/forgot") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com"}"""
        }.andExpect {
            status { isTooManyRequests() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("rate_limit_exceeded") }
            header { string("Retry-After", "60") }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityPasswordRecovery(): StubIdentityPasswordRecovery = StubIdentityPasswordRecovery()
    }

    class StubIdentityPasswordRecovery : IdentityPasswordRecovery {
        var outcome: RequestPasswordResetOutcome = RequestPasswordResetOutcome.Accepted
        val commands = mutableListOf<RequestPasswordResetCommand>()

        override fun requestPasswordReset(command: RequestPasswordResetCommand): RequestPasswordResetOutcome {
            commands += command
            return outcome
        }
    }
}
