package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.identity.api.IdentityPasswordRecovery
import br.com.mykytadu.identity.api.RequestPasswordResetCommand
import br.com.mykytadu.identity.api.RequestPasswordResetOutcome
import br.com.mykytadu.identity.api.ResetPasswordCommand
import br.com.mykytadu.identity.api.ResetPasswordOutcome
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
        recovery.resetOutcome = ResetPasswordOutcome.Reset
        recovery.commands.clear()
        recovery.resetCommands.clear()
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

    @Test
    fun `returns no content on successful password reset`() {
        val response = mockMvc.post("/api/v1/auth/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionToken":"valid-token","newPassword":"valid-password-123"}"""
        }.andExpect {
            status { isNoContent() }
        }.andReturn().response

        assertThat(response.contentAsString).isEmpty()
        assertThat(recovery.resetCommands.single().actionToken).isEqualTo("valid-token")
        assertThat(recovery.resetCommands.single().newPassword).isEqualTo("valid-password-123")
    }

    @Test
    fun `rejects invalid or expired token with invalid_action_token problem`() {
        recovery.resetOutcome = ResetPasswordOutcome.InvalidToken

        mockMvc.post("/api/v1/auth/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionToken":"invalid-token","newPassword":"valid-password-123"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("invalid_action_token") }
        }
    }

    @Test
    fun `rejects short password with request_validation_failed`() {
        mockMvc.post("/api/v1/auth/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionToken":"valid-token","newPassword":"short"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.errors") { isNotEmpty() }
        }

        assertThat(recovery.resetCommands).isEmpty()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityPasswordRecovery(): StubIdentityPasswordRecovery = StubIdentityPasswordRecovery()
    }

    class StubIdentityPasswordRecovery : IdentityPasswordRecovery {
        var outcome: RequestPasswordResetOutcome = RequestPasswordResetOutcome.Accepted
        var resetOutcome: ResetPasswordOutcome = ResetPasswordOutcome.Reset
        val commands = mutableListOf<RequestPasswordResetCommand>()
        val resetCommands = mutableListOf<ResetPasswordCommand>()

        override fun requestPasswordReset(command: RequestPasswordResetCommand): RequestPasswordResetOutcome {
            commands += command
            return outcome
        }

        override fun resetPassword(command: ResetPasswordCommand): ResetPasswordOutcome {
            resetCommands += command
            return resetOutcome
        }
    }
}
