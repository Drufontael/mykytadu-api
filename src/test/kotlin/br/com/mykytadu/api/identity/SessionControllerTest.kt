package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.security.ProblemAccessDeniedHandler
import br.com.mykytadu.api.security.ProblemAuthenticationEntryPoint
import br.com.mykytadu.api.security.SecurityConfiguration
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.CsrfOutcome
import br.com.mykytadu.identity.api.IdentityLogout
import br.com.mykytadu.identity.api.IdentitySession
import br.com.mykytadu.identity.api.LogoutAllSessionsCommand
import br.com.mykytadu.identity.api.LogoutOutcome
import br.com.mykytadu.identity.api.LogoutSessionCommand
import br.com.mykytadu.identity.api.RefreshOutcome
import br.com.mykytadu.identity.api.RefreshSessionCommand
import br.com.mykytadu.identity.api.RefreshedSession
import br.com.mykytadu.identity.api.ReissueCsrfCommand
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(SessionController::class)
@AutoConfigureMockMvc
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    SessionControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class SessionControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val sessions: StubIdentitySession,
) {

    @BeforeEach
    fun reset() {
        sessions.csrfOutcome = CsrfOutcome.Issued("csrf-token")
        sessions.refreshOutcome = RefreshOutcome.Refreshed(refreshed(AuthenticationClient.ANDROID))
        sessions.refreshCommands.clear()
        sessions.logoutOutcome = LogoutOutcome.Completed
        sessions.logoutAllOutcome = LogoutOutcome.Completed
        sessions.logoutCommands.clear()
        sessions.logoutAllCommands.clear()
    }

    @Test
    fun `reissues csrf from protected refresh cookie`() {
        mockMvc.get("/api/v1/auth/csrf") {
            cookie(jakarta.servlet.http.Cookie("mykytadu_refresh", "refresh-token"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.csrfToken") { value("csrf-token") }
        }
    }

    @Test
    fun `returns native refresh in body and requires idempotency key`() {
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            header("Idempotency-Key", "request-1")
            content = """{"refreshToken":"refresh-token"}"""
        }.andExpect {
            status { isOk() }
            header { doesNotExist(HttpHeaders.SET_COOKIE) }
            jsonPath("$.refreshToken") { value("next-refresh") }
        }
        assertThat(sessions.refreshCommands.single().web).isFalse()

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"refresh-token"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `rotates Web cookie without exposing refresh in body`() {
        sessions.refreshOutcome = RefreshOutcome.Refreshed(refreshed(AuthenticationClient.WEB))

        mockMvc.post("/api/v1/auth/refresh") {
            header("Idempotency-Key", "request-1")
            header(HttpHeaders.ORIGIN, "http://localhost:8080")
            header("X-CSRF-Token", "csrf-token")
            cookie(jakarta.servlet.http.Cookie("mykytadu_refresh", "refresh-token"))
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.SET_COOKIE, containsString("mykytadu_refresh=next-refresh")) }
            jsonPath("$.refreshToken") { doesNotExist() }
            jsonPath("$.csrfToken") { value("next-csrf") }
        }
    }

    @Test
    fun `logs out a native session bound to the authenticated principal`() {
        mockMvc.post("/api/v1/auth/logout") {
            with(jwt().jwt { it.subject(USER_ID.toString()).claim("client_id", "mykytadu-android") })
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"refresh-token"}"""
        }.andExpect {
            status { isNoContent() }
            header { doesNotExist(HttpHeaders.SET_COOKIE) }
        }
        assertThat(sessions.logoutCommands.single().userId).isEqualTo(USER_ID)
        assertThat(sessions.logoutCommands.single().refreshToken).isEqualTo("refresh-token")
        assertThat(sessions.logoutCommands.single().web).isFalse()
    }

    @Test
    fun `logs out a Web session with CSRF and clears refresh cookie`() {
        mockMvc.post("/api/v1/auth/logout") {
            with(jwt().jwt { it.subject(USER_ID.toString()).claim("client_id", "mykytadu-web") })
            header(HttpHeaders.ORIGIN, "http://localhost:8080")
            header("X-CSRF-Token", "csrf-token")
            cookie(jakarta.servlet.http.Cookie("mykytadu_refresh", "refresh-token"))
        }.andExpect {
            status { isNoContent() }
            header { string(HttpHeaders.SET_COOKIE, containsString("mykytadu_refresh=;")) }
            header { string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")) }
        }
        assertThat(sessions.logoutCommands.single().web).isTrue()
    }

    @Test
    fun `logs out all sessions and clears cookie for Web principal`() {
        mockMvc.post("/api/v1/auth/logout-all") {
            with(jwt().jwt { it.subject(USER_ID.toString()).claim("client_id", "mykytadu-web") })
            header(HttpHeaders.ORIGIN, "http://localhost:8080")
            header("X-CSRF-Token", "csrf-token")
            cookie(jakarta.servlet.http.Cookie("mykytadu_refresh", "refresh-token"))
        }.andExpect {
            status { isNoContent() }
            header { string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")) }
        }
        assertThat(sessions.logoutAllCommands.single().userId).isEqualTo(USER_ID)
        assertThat(sessions.logoutAllCommands.single().currentRefreshToken).isEqualTo("refresh-token")
        assertThat(sessions.logoutAllCommands.single().web).isTrue()
    }

    @Test
    fun `returns csrf problem and does not clear cookie when Web logout fails validation`() {
        sessions.logoutOutcome = LogoutOutcome.CsrfInvalid
        mockMvc.post("/api/v1/auth/logout") {
            with(jwt().jwt { it.subject(USER_ID.toString()).claim("client_id", "mykytadu-web") })
            header(HttpHeaders.ORIGIN, "http://localhost:8080")
            cookie(jakarta.servlet.http.Cookie("mykytadu_refresh", "refresh-token"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("csrf_invalid") }
            header { doesNotExist(HttpHeaders.SET_COOKIE) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/auth/logout", "/api/v1/auth/logout-all"])
    fun `requires bearer authentication for logout operations`(path: String) {
        mockMvc.post(path).andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("authentication_required") }
        }
    }

    private fun refreshed(client: AuthenticationClient) = RefreshedSession(
        accessToken = "access-token",
        expiresInSeconds = 600,
        refreshExpiresInSeconds = 2_592_000,
        refreshToken = "next-refresh",
        csrfToken = "next-csrf".takeIf { client.web },
        principal = PRINCIPAL,
        client = client,
    )

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identitySession() = StubIdentitySession()

        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder { throw BadJwtException("Invalid test token") }
    }

    class StubIdentitySession :
        IdentitySession,
        IdentityLogout {
        lateinit var csrfOutcome: CsrfOutcome
        lateinit var refreshOutcome: RefreshOutcome
        var logoutOutcome: LogoutOutcome = LogoutOutcome.Completed
        var logoutAllOutcome: LogoutOutcome = LogoutOutcome.Completed
        val refreshCommands = mutableListOf<RefreshSessionCommand>()
        val logoutCommands = mutableListOf<LogoutSessionCommand>()
        val logoutAllCommands = mutableListOf<LogoutAllSessionsCommand>()

        override fun reissueCsrf(command: ReissueCsrfCommand): CsrfOutcome = csrfOutcome

        override fun refresh(command: RefreshSessionCommand): RefreshOutcome {
            refreshCommands += command
            return refreshOutcome
        }

        override fun logout(command: LogoutSessionCommand): LogoutOutcome {
            logoutCommands += command
            return logoutOutcome
        }

        override fun logoutAll(command: LogoutAllSessionsCommand): LogoutOutcome {
            logoutAllCommands += command
            return logoutAllOutcome
        }
    }

    companion object {
        private val USER_ID = UUID.fromString("019937b6-3600-7001-8000-000000000001")
        private val NOW = Instant.parse("2026-09-27T16:00:00Z")
        private val PRINCIPAL = AuthenticatedPrincipal(
            id = UUID.fromString("019937b6-3600-7001-8000-000000000001"),
            email = "person@example.com",
            displayName = "Person",
            roles = setOf("USER"),
            emailVerifiedAt = NOW,
            createdAt = NOW.minusSeconds(60),
            updatedAt = NOW,
        )
    }
}
