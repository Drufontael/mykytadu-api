package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.identity.api.EmailVerificationOutcome
import br.com.mykytadu.identity.api.IdentityRegistration
import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegisteredUser
import br.com.mykytadu.identity.api.RegistrationOutcome
import br.com.mykytadu.identity.api.ResendVerificationCommand
import br.com.mykytadu.identity.api.ResendVerificationOutcome
import br.com.mykytadu.identity.api.VerifyEmailCommand
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
import java.time.Instant
import java.util.UUID

@WebMvcTest(RegistrationController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    RequestKeyFactory::class,
    RegistrationControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class RegistrationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val registration: StubIdentityRegistration,
) {

    @BeforeEach
    fun resetStub() {
        registration.registrationOutcome = RegistrationOutcome.Created(USER)
        registration.resendOutcome = ResendVerificationOutcome.Accepted
        registration.verificationOutcome = EmailVerificationOutcome.Verified
        registration.registeredCommands.clear()
        registration.verificationCommands.clear()
    }

    @Test
    fun `creates a pending account without exposing credentials`() {
        val response = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = REGISTER_REQUEST
        }.andExpect {
            status { isCreated() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.user.id") { value(USER.id.toString()) }
            jsonPath("$.user.status") { value("pending") }
            jsonPath("$.user.roles[0]") { value("USER") }
            jsonPath("$.nextAction") { value("verifyEmail") }
            jsonPath("$.password") { doesNotExist() }
            jsonPath("$.actionToken") { doesNotExist() }
        }.andReturn().response

        assertThat(response.contentAsString).doesNotContain("secure-password", "argon2", "token")
        assertThat(registration.registeredCommands.single().requestKey).hasSize(64)
    }

    @Test
    fun `rejects invalid requests before invoking Identity`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"invalid","password":"short","displayName":"   "}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.errors") { isNotEmpty() }
        }

        assertThat(registration.registeredCommands).isEmpty()
    }

    @Test
    fun `maps duplicate delivery and rate failures to stable problems`() {
        registration.registrationOutcome = RegistrationOutcome.Rejected
        assertRegistrationProblem(409, "registration_rejected", null)

        registration.registrationOutcome = RegistrationOutcome.DeliveryUnavailable(60)
        assertRegistrationProblem(503, "email_delivery_unavailable", "60")

        registration.registrationOutcome = RegistrationOutcome.RateLimited(30)
        assertRegistrationProblem(429, "rate_limit_exceeded", "30")
    }

    @Test
    fun `returns uniform accepted response for resend`() {
        mockMvc.post("/api/v1/auth/verify-email/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.accepted") { value(true) }
        }
    }

    @Test
    fun `verifies an email without returning the action token`() {
        val response = mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionToken":"opaque-verification-token"}"""
        }.andExpect {
            status { isNoContent() }
        }.andReturn().response

        assertThat(response.contentAsString).isEmpty()
        assertThat(registration.verificationCommands.single().actionToken)
            .isEqualTo("opaque-verification-token")
    }

    @Test
    fun `maps an unusable action token to the stable problem`() {
        registration.verificationOutcome = EmailVerificationOutcome.Invalid

        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionToken":"unusable-token"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("invalid_action_token") }
            jsonPath("$.errors") { isEmpty() }
        }
    }

    private fun assertRegistrationProblem(status: Int, code: String, retryAfter: String?) {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = REGISTER_REQUEST
        }.andExpect {
            status { isEqualTo(status) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value(code) }
            jsonPath("$.errors") { isEmpty() }
            if (retryAfter == null) {
                header { doesNotExist("Retry-After") }
            } else {
                header { string("Retry-After", retryAfter) }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityRegistration(): StubIdentityRegistration = StubIdentityRegistration()
    }

    class StubIdentityRegistration : IdentityRegistration {
        var registrationOutcome: RegistrationOutcome = RegistrationOutcome.Created(USER)
        var resendOutcome: ResendVerificationOutcome = ResendVerificationOutcome.Accepted
        var verificationOutcome: EmailVerificationOutcome = EmailVerificationOutcome.Verified
        val registeredCommands = mutableListOf<RegisterAccountCommand>()
        val verificationCommands = mutableListOf<VerifyEmailCommand>()

        override fun register(command: RegisterAccountCommand): RegistrationOutcome {
            registeredCommands += command
            return registrationOutcome
        }

        override fun resendVerification(command: ResendVerificationCommand): ResendVerificationOutcome = resendOutcome

        override fun verifyEmail(command: VerifyEmailCommand): EmailVerificationOutcome {
            verificationCommands += command
            return verificationOutcome
        }
    }

    companion object {
        private const val REGISTER_REQUEST =
            """{"email":"person@example.com","password":"a-secure-password","displayName":"Person"}"""
        private val NOW = Instant.parse("2026-09-19T12:00:00Z")
        private val USER = RegisteredUser(
            id = UUID.fromString("0199204a-1200-7001-8000-000000000001"),
            email = "person@example.com",
            displayName = "Person",
            status = "pending",
            roles = linkedSetOf("USER"),
            emailVerifiedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }
}
