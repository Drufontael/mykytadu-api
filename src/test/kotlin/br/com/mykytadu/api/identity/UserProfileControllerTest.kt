package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.security.ProblemAccessDeniedHandler
import br.com.mykytadu.api.security.ProblemAuthenticationEntryPoint
import br.com.mykytadu.api.security.SecurityConfiguration
import br.com.mykytadu.identity.api.GetProfileQuery
import br.com.mykytadu.identity.api.IdentityProfile
import br.com.mykytadu.identity.api.ProfileOutcome
import br.com.mykytadu.identity.api.UpdateProfileCommand
import br.com.mykytadu.identity.api.UserProfileDto
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.UUID

@WebMvcTest(UserProfileController::class)
@AutoConfigureMockMvc
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    UserProfileControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class UserProfileControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val profile: StubIdentityProfile,
) {

    @BeforeEach
    fun reset() {
        profile.getOutcome = ProfileOutcome.Success(DEFAULT_PROFILE)
        profile.updateOutcome = ProfileOutcome.Success(DEFAULT_PROFILE)
        profile.getQueries.clear()
        profile.updateCommands.clear()
    }

    @Test
    fun `gets current authenticated user profile`() {
        mockMvc.get("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(USER_ID.toString()) }
            jsonPath("$.email") { value("person@example.com") }
            jsonPath("$.displayName") { value("Person") }
            jsonPath("$.status") { value("active") }
            jsonPath("$.roles[0]") { value("USER") }
            jsonPath("$.emailVerifiedAt") { value("2026-09-27T16:00:00Z") }
            jsonPath("$.createdAt") { value("2026-09-27T15:00:00Z") }
            jsonPath("$.updatedAt") { value("2026-09-27T16:00:00Z") }
        }

        assertThat(profile.getQueries.single().userId).isEqualTo(USER_ID)
    }

    @Test
    fun `returns 401 when user is not found on query`() {
        profile.getOutcome = ProfileOutcome.NotFound

        mockMvc.get("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
        }.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("authentication_required") }
        }
    }

    @Test
    fun `returns 401 when token is missing on get`() {
        mockMvc.get("/api/v1/me").andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("authentication_required") }
        }
    }

    @Test
    fun `updates display name successfully`() {
        val updated = DEFAULT_PROFILE.copy(displayName = "Novo Nome")
        profile.updateOutcome = ProfileOutcome.Success(updated)

        mockMvc.patch("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayName":"Novo Nome"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Novo Nome") }
        }

        assertThat(profile.updateCommands.single().userId).isEqualTo(USER_ID)
        assertThat(profile.updateCommands.single().displayName).isEqualTo("Novo Nome")
    }

    @Test
    fun `clears display name to null successfully`() {
        val updated = DEFAULT_PROFILE.copy(displayName = null)
        profile.updateOutcome = ProfileOutcome.Success(updated)

        mockMvc.patch("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayName":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { doesNotExist() }
        }

        assertThat(profile.updateCommands.single().userId).isEqualTo(USER_ID)
        assertThat(profile.updateCommands.single().displayName).isNull()
    }

    @Test
    fun `returns 401 when token is missing on patch`() {
        mockMvc.patch("/api/v1/me") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayName":"Novo Nome"}"""
        }.andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("authentication_required") }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "{}",
            """{"displayName":""}""",
            """{"displayName":"   "}""",
            """{"email":"new@example.com"}""",
            """{"status":"blocked"}""",
            """{"roles":["ADMIN"]}""",
            """{"id":"019937b6-3600-7001-8000-000000000001"}""",
            """{"createdAt":"2026-09-27T15:00:00Z"}""",
            """{"updatedAt":"2026-09-27T16:00:00Z"}""",
            """{"displayName":"Valid","email":"extra@example.com"}""",
            "invalid-json",
        ],
    )
    fun `rejects invalid or unauthorized patch payloads`(payload: String) {
        mockMvc.patch("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = payload
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("request_validation_failed") }
        }

        assertThat(profile.updateCommands).isEmpty()
    }

    @Test
    fun `rejects display name exceeding 80 characters`() {
        mockMvc.patch("/api/v1/me") {
            with(jwt().jwt { it.subject(USER_ID.toString()) })
            contentType = MediaType.APPLICATION_JSON
            content = """{"displayName":"${"a".repeat(81)}"}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("request_validation_failed") }
        }

        assertThat(profile.updateCommands).isEmpty()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityProfile() = StubIdentityProfile()

        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder { throw BadJwtException("Invalid test token") }
    }

    class StubIdentityProfile : IdentityProfile {
        lateinit var getOutcome: ProfileOutcome
        lateinit var updateOutcome: ProfileOutcome
        val getQueries = mutableListOf<GetProfileQuery>()
        val updateCommands = mutableListOf<UpdateProfileCommand>()

        override fun getProfile(query: GetProfileQuery): ProfileOutcome {
            getQueries += query
            return getOutcome
        }

        override fun updateProfile(command: UpdateProfileCommand): ProfileOutcome {
            updateCommands += command
            return updateOutcome
        }
    }

    companion object {
        private val USER_ID = UUID.fromString("019937b6-3600-7001-8000-000000000001")
        private val NOW = Instant.parse("2026-09-27T16:00:00Z")
        private val DEFAULT_PROFILE = UserProfileDto(
            id = USER_ID,
            email = "person@example.com",
            displayName = "Person",
            status = "active",
            roles = setOf("USER"),
            emailVerifiedAt = NOW,
            createdAt = NOW.minusSeconds(3600),
            updatedAt = NOW,
        )
    }
}
