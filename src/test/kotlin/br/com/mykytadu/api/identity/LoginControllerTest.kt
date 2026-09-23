package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.IdentityLogin
import br.com.mykytadu.identity.api.InitialSession
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(LoginController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    RequestKeyFactory::class,
    LoginControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class LoginControllerTest(@Autowired private val mockMvc: MockMvc, @Autowired private val login: StubIdentityLogin) {

    @BeforeEach
    fun reset() {
        login.outcome = LoginOutcome.Created(session(AuthenticationClient.ANDROID))
        login.commands.clear()
    }

    @Test
    fun `returns refresh only in the native response`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com","password":"password","clientId":"mykytadu-android"}"""
        }.andExpect {
            status { isOk() }
            header { doesNotExist(HttpHeaders.SET_COOKIE) }
            jsonPath("$.accessToken") { value("access-token") }
            jsonPath("$.expiresIn") { value(600) }
            jsonPath("$.refreshToken") { value("refresh-token") }
            jsonPath("$.csrfToken") { doesNotExist() }
            jsonPath("$.user.status") { value("active") }
        }

        assertThat(login.commands.single().client).isEqualTo(AuthenticationClient.ANDROID)
        assertThat(login.commands.single().requestKey).hasSize(64)
    }

    @Test
    fun `returns Web refresh only in the protected cookie`() {
        login.outcome = LoginOutcome.Created(session(AuthenticationClient.WEB))

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            header(HttpHeaders.ORIGIN, "http://localhost:8080")
            content = """{"email":"person@example.com","password":"password","clientId":"mykytadu-web"}"""
        }.andExpect {
            status { isOk() }
            header {
                string(
                    HttpHeaders.SET_COOKIE,
                    allOf(
                        containsString("mykytadu_refresh=refresh-token"),
                        containsString("Path=/api/v1/auth"),
                        containsString("Max-Age=2592000"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                    ),
                )
            }
            jsonPath("$.refreshToken") { doesNotExist() }
            jsonPath("$.csrfToken") { value("csrf-token") }
        }
    }

    @Test
    fun `uses legacy native mode when client id is absent`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com","password":"password"}"""
        }.andExpect { status { isOk() } }

        assertThat(login.commands.single().client).isEqualTo(AuthenticationClient.LEGACY_NATIVE)
    }

    @Test
    fun `maps authentication failures and rate limit to stable problems`() {
        login.outcome = LoginOutcome.InvalidCredentials
        assertProblem(401, "invalid_credentials")

        login.outcome = LoginOutcome.EmailVerificationRequired
        assertProblem(401, "email_verification_required")

        login.outcome = LoginOutcome.InvalidOrigin
        assertProblem(403, "csrf_invalid")

        login.outcome = LoginOutcome.RateLimited(30)
        assertProblem(429, "rate_limit_exceeded", "30")
    }

    @Test
    fun `rejects an unsupported client id before authentication`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com","password":"password","clientId":"unknown"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("request_validation_failed") }
        }

        assertThat(login.commands).isEmpty()
    }

    private fun assertProblem(status: Int, code: String, retryAfter: String? = null) {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"person@example.com","password":"password"}"""
        }.andExpect {
            status { isEqualTo(status) }
            jsonPath("$.code") { value(code) }
            if (retryAfter == null) {
                header { doesNotExist(HttpHeaders.RETRY_AFTER) }
            } else {
                header { string(HttpHeaders.RETRY_AFTER, retryAfter) }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityLogin(): StubIdentityLogin = StubIdentityLogin()
    }

    class StubIdentityLogin : IdentityLogin {
        lateinit var outcome: LoginOutcome
        val commands = mutableListOf<LoginCommand>()

        override fun login(command: LoginCommand): LoginOutcome {
            commands += command
            return outcome
        }
    }

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val PRINCIPAL = AuthenticatedPrincipal(
            UUID.fromString("019937b6-3600-7001-8000-000000000001"),
            "person@example.com",
            "Person",
            setOf("USER"),
            NOW,
            NOW.minusSeconds(60),
            NOW,
        )

        private fun session(client: AuthenticationClient) = InitialSession(
            accessToken = "access-token",
            expiresInSeconds = 600,
            refreshExpiresInSeconds = 2_592_000,
            refreshToken = "refresh-token",
            csrfToken = "csrf-token".takeIf { client.web },
            principal = PRINCIPAL,
            client = client,
        )
    }
}
