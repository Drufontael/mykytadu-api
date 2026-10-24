package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
internal class KmpContractCompatibilityIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val idGenerator: IdentityIdGenerator,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearDatabase() {
        jdbcTemplate.update("TRUNCATE TABLE translation.usage_daily, translation.translations")
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `unauthenticated access to protected endpoints adheres to RFC 9457 problem details`() {
        // GET /api/v1/me sem credencial
        assertProblemDetailsStructure(
            resultActions = mockMvc.perform(get("/api/v1/me")),
            expectedStatus = 401,
            expectedCode = "authentication_required",
            expectedInstance = "/api/v1/me",
        ).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))

        // POST /api/v1/translations sem credencial
        assertProblemDetailsStructure(
            resultActions = mockMvc.perform(
                post("/api/v1/translations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "text": "Hello world",
                            "sourceLanguage": "en",
                            "targetLanguage": "pt-BR",
                            "contentType": "ANIME_DESCRIPTION"
                        }
                        """.trimIndent(),
                    ),
            ),
            expectedStatus = 401,
            expectedCode = "authentication_required",
            expectedInstance = "/api/v1/translations",
        ).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
    }

    @Test
    fun `request validation failures adhere to RFC 9457 with field errors array`() {
        val result = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "email": "not-an-email",
                        "password": "",
                        "displayName": "Deku"
                    }
                    """.trimIndent(),
                ),
        )

        assertProblemDetailsStructure(
            resultActions = result,
            expectedStatus = 400,
            expectedCode = "request_validation_failed",
            expectedInstance = "/api/v1/auth/register",
        )
            .andExpect(jsonPath("$.errors").isArray)
            .andExpect(jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty)
            .andExpect(jsonPath("$.errors[?(@.field == 'password')]").isNotEmpty)
    }

    @Test
    fun `business errors produce stable codes for KMP client decision making`() {
        createActiveUser("kmp-client@example.com", "CorrectPassword123!")

        // 1. Credenciais inválidas
        assertProblemDetailsStructure(
            resultActions = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "email": "kmp-client@example.com",
                            "password": "WrongPassword999!",
                            "clientId": "mykytadu-android"
                        }
                        """.trimIndent(),
                    ),
            ),
            expectedStatus = 401,
            expectedCode = "invalid_credentials",
            expectedInstance = "/api/v1/auth/login",
        )

        // 2. Token de ação inválido / inexistente
        assertProblemDetailsStructure(
            resultActions = mockMvc.perform(
                post("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"actionToken":"non-existent-token"}"""),
            ),
            expectedStatus = 400,
            expectedCode = "invalid_action_token",
            expectedInstance = "/api/v1/auth/verify-email",
        )

        // 3. Refresh token inválido
        assertProblemDetailsStructure(
            resultActions = mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"invalid-refresh-token"}"""),
            ),
            expectedStatus = 401,
            expectedCode = "session_invalid",
            expectedInstance = "/api/v1/auth/refresh",
        )
    }

    @Test
    fun `successful responses adhere strictly to camelCase and ISO-8601 UTC formats`() {
        val registerResult = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "email": "kmp-contract@example.com",
                        "password": "ValidPassword123!",
                        "displayName": "Kmp User"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user.id").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("kmp-contract@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("Kmp User"))
            .andExpect(jsonPath("$.user.status").value("pending"))
            .andExpect(jsonPath("$.user.roles[0]").value("USER"))
            .andReturn()

        val json = registerResult.response.contentAsString
        // Formato ISO-8601 UTC verificado
        assertThat(json).containsPattern(""""createdAt":"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
        assertThat(json).containsPattern(""""updatedAt":"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
    }

    private fun assertProblemDetailsStructure(
        resultActions: ResultActions,
        expectedStatus: Int,
        expectedCode: String,
        expectedInstance: String,
    ): ResultActions = resultActions
        .andExpect(status().`is`(expectedStatus))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:mykytadu:problem:$expectedCode"))
        .andExpect(jsonPath("$.title").isNotEmpty)
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.instance").value(expectedInstance))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.traceId").isNotEmpty)
        .andExpect(jsonPath("$.errors").isArray)

    private fun createActiveUser(email: String, rawPassword: CharSequence) {
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val timestamp = Timestamp.from(now)
        val userId = idGenerator.nextUserId().value
        val normalizedEmail = email.trim().lowercase()
        val hash = passwordHasher.hash(rawPassword)

        jdbcTemplate.update(
            "INSERT INTO identity.users " +
                "(id, email, normalized_email, display_name, status, email_verified_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            userId,
            email,
            normalizedEmail,
            "Kmp User",
            "active",
            timestamp,
            timestamp,
            timestamp,
        )
        jdbcTemplate.update(
            "INSERT INTO identity.password_credentials (user_id, password_hash, algorithm, updated_at) " +
                "VALUES (?, ?, ?, ?)",
            userId,
            hash.encodedValue(),
            "argon2id",
            timestamp,
        )
        jdbcTemplate.update(
            "INSERT INTO identity.roles (user_id, role) VALUES (?, ?)",
            userId,
            "USER",
        )
    }

    companion object {
        @Container
        @ServiceConnection
        private val postgres = PostgreSQLContainer("postgres:18.6-alpine").apply {
            withDatabaseName("mykytadu")
            withUsername("mykytadu_test")
            withPassword("mykytadu_test")
        }
    }
}
