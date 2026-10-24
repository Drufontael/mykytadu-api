package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.ActionTokenCryptography
import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
internal class IdentityEndToEndIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val tokenCryptography: ActionTokenCryptography,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val idGenerator: IdentityIdGenerator,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearDatabase() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `complete registration, email verification, and login flow`() {
        // 1. Registro e validação de pendência
        registerUser("hero@example.com", "ValidPassword123!", "Deku")

        // 2. Login antes de verificar o e-mail falha com 401
        attemptLogin("hero@example.com", "ValidPassword123!")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("email_verification_required"))

        // 3. Atualizar token e verificar e-mail
        val rawToken = "known-verification-token-12345"
        updateActionTokenHash("email_verification", "hero@example.com", rawToken)

        mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionToken":"$rawToken"}"""),
        ).andExpect(status().isNoContent)

        // 4. Verificação duplicada rejeitada
        mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionToken":"$rawToken"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid_action_token"))

        // 5. Login bem-sucedido e acesso ao perfil
        val (accessToken, _) = loginSuccessfully("hero@example.com", "ValidPassword123!")

        mockMvc.perform(
            get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("hero@example.com"))
            .andExpect(jsonPath("$.displayName").value("Deku"))

        // 6. Atualizar perfil com PATCH
        mockMvc.perform(
            patch("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"Izuku Midoriya"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Izuku Midoriya"))
    }

    @Test
    fun `refresh token rotation, reuse detection, and session revocation`() {
        createActiveUser("refresh-flow@example.com", "Password123!")

        // 1. Login inicial
        val (accessToken1, refreshToken1) = loginSuccessfully("refresh-flow@example.com", "Password123!")

        // 2. Rotação do refresh token
        val refreshResult = mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val accessToken2: String = JsonPath.read(refreshResult.response.contentAsString, "$.accessToken")
        val refreshToken2: String = JsonPath.read(refreshResult.response.contentAsString, "$.refreshToken")

        assertThat(accessToken2).isNotEqualTo(accessToken1)
        assertThat(refreshToken2).isNotEqualTo(refreshToken1)

        // 3. Novo token de acesso funciona
        mockMvc.perform(
            get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken2"),
        ).andExpect(status().isOk)

        // 4. Detecção de reuso: token antigo revoga a família
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken1"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("session_invalid"))

        // 5. Família inteira invalidada
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken2"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("session_invalid"))
    }

    @Test
    fun `logout and logout all sessions flow`() {
        createActiveUser("logout-flow@example.com", "Password123!")

        // 1. Login e logout individual
        val (accessToken1, refreshToken1) = loginSuccessfully("logout-flow@example.com", "Password123!")

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken1"}"""),
        ).andExpect(status().isNoContent)

        // 2. Token invalidado após logout
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken1"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("session_invalid"))

        // 3. Login novamente e logout global
        val (accessToken2, refreshToken2) = loginSuccessfully("logout-flow@example.com", "Password123!")

        mockMvc.perform(
            post("/api/v1/auth/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken2"),
        ).andExpect(status().isNoContent)

        // 4. Todas as sessões revogadas globalmente
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$refreshToken2"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("session_invalid"))
    }

    @Test
    fun `password recovery and reset flow`() {
        createActiveUser("recovery-flow@example.com", "InitialPassword123!")

        // 1. Solicitar recuperação e vincular token de teste
        mockMvc.perform(
            post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"recovery-flow@example.com"}"""),
        ).andExpect(status().isAccepted)

        val knownResetToken = "known-password-reset-token-999"
        updateActionTokenHash("password_reset", "recovery-flow@example.com", knownResetToken)

        // 2. Resetar a senha com sucesso
        resetPassword(knownResetToken, "NewSecretPassword123!")
            .andExpect(status().isNoContent)

        // 3. Senha antiga não funciona mais
        attemptLogin("recovery-flow@example.com", "InitialPassword123!")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("invalid_credentials"))

        // 4. Nova senha autentica normalmente
        loginSuccessfully("recovery-flow@example.com", "NewSecretPassword123!")

        // 5. Token consumido não pode ser reutilizado
        resetPassword(knownResetToken, "AnotherPassword123!")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid_action_token"))
    }

    private fun registerUser(email: String, password: String, displayName: String) {
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "email": "$email",
                        "password": "$password",
                        "displayName": "$displayName"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.user.status").value("pending"))
            .andExpect(jsonPath("$.user.email").value(email))
    }

    private fun attemptLogin(email: String, password: String) = mockMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                    "email": "$email",
                    "password": "$password",
                    "clientId": "mykytadu-android"
                }
                """.trimIndent(),
            ),
    )

    private fun loginSuccessfully(email: String, password: String): Pair<String, String> {
        val loginResult = attemptLogin(email, password)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val accessToken: String = JsonPath.read(loginResult.response.contentAsString, "$.accessToken")
        val refreshToken: String = JsonPath.read(loginResult.response.contentAsString, "$.refreshToken")
        return accessToken to refreshToken
    }

    private fun resetPassword(token: String, newPassword: String) = mockMvc.perform(
        post("/api/v1/auth/password/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                    "actionToken": "$token",
                    "newPassword": "$newPassword"
                }
                """.trimIndent(),
            ),
    )

    private fun updateActionTokenHash(type: String, email: String, token: String) {
        val knownHash = tokenCryptography.hash(token)
        jdbcTemplate.update(
            "UPDATE identity.action_tokens SET token_hash = ? " +
                "WHERE type = ? AND user_id = (" +
                "SELECT id FROM identity.users WHERE normalized_email = ?)",
            knownHash.persistenceValue(),
            type,
            email.trim().lowercase(),
        )
    }

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
            "Active User",
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
