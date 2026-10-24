package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.translation.application.PurgeExpiredTranslationsUseCase
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
internal class TranslationEndToEndIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val idGenerator: IdentityIdGenerator,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val purgeUseCase: PurgeExpiredTranslationsUseCase,
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
    fun `initial miss translates, stores canonical cache without original text, and returns cached false`() {
        val userEmail = "translator-miss@example.com"
        val userId = createActiveUser(userEmail, "Password123!")
        val accessToken = loginSuccessfully(userEmail, "Password123!")

        val text = "A spirited adventure across distant galaxies."

        sendTranslationRequest(accessToken, text)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cached").value(false))
            .andExpect(jsonPath("$.translation").value("[PT-BR] $text"))
            .andExpect(jsonPath("$.targetLanguage").value("pt-BR"))
            .andExpect(jsonPath("$.contentType").value("ANIME_DESCRIPTION"))

        // Validação da persistência canônica e privacidade (ADR-008)
        val rows = jdbcTemplate.queryForList("SELECT * FROM translation.translations")
        assertThat(rows).hasSize(1)

        val row = rows.first()
        val contentHash = row["content_hash"] as String
        val storedTranslation = row["translation"] as String

        assertThat(contentHash).hasSize(64)
        assertThat(storedTranslation).isEqualTo("[PT-BR] $text")

        // O schema não possui coluna de texto original (ADR-008)
        val columnNames = row.keys.map { it.lowercase() }
        assertThat(columnNames).doesNotContain("text", "original_text", "source_text", "content")

        // Uso diário acumulado para o usuário
        val usageCount = jdbcTemplate.queryForObject(
            "SELECT characters_count FROM translation.usage_daily WHERE user_id = ?",
            Long::class.java,
            userId,
        )
        assertThat(usageCount).isEqualTo(text.length.toLong())
    }

    @Test
    fun `subsequent request returns cache hit without incrementing character usage`() {
        val userEmail = "translator-hit@example.com"
        val userId = createActiveUser(userEmail, "Password123!")
        val accessToken = loginSuccessfully(userEmail, "Password123!")

        val originalText = "A spirited adventure across distant galaxies."
        sendTranslationRequest(accessToken, originalText).andExpect(status().isOk)

        // Segundo request com espaços adicionais que o normalizador canônico unifica
        val textWithSpaces = "   A spirited adventure across distant galaxies.   "
        sendTranslationRequest(accessToken, textWithSpaces)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cached").value(true))
            .andExpect(jsonPath("$.translation").value("[PT-BR] $originalText"))

        val translationRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM translation.translations",
            Long::class.java,
        )
        assertThat(translationRows).isEqualTo(1L)

        // Hit de cache não consome quota diária adicional
        val usageCount = jdbcTemplate.queryForObject(
            "SELECT characters_count FROM translation.usage_daily WHERE user_id = ?",
            Long::class.java,
            userId,
        )
        assertThat(usageCount).isEqualTo(originalText.length.toLong())
    }

    @Test
    fun `preserves safe html markup in translated content`() {
        val userEmail = "translator-html@example.com"
        createActiveUser(userEmail, "Password123!")
        val accessToken = loginSuccessfully(userEmail, "Password123!")

        val htmlText = "<b>Deku</b> is an <i>inspiring</i> hero!"

        sendTranslationRequest(accessToken, htmlText)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cached").value(false))
            .andExpect(jsonPath("$.translation").value("[PT-BR] <b>Deku</b> is an <i>inspiring</i> hero!"))
    }

    @Test
    fun `exceeding daily character quota returns 429 too many requests with retry after`() {
        val userEmail = "translator-quota@example.com"
        val userId = createActiveUser(userEmail, "Password123!")
        val accessToken = loginSuccessfully(userEmail, "Password123!")

        // Inserir uso diário prévio próximo ao limite de 50.000 caracteres
        val today = LocalDate.now()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO translation.usage_daily " +
                "(id, user_id, usage_date, characters_count, requests_count, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            userId,
            today,
            49990L,
            10,
            Timestamp.from(now),
            Timestamp.from(now),
        )

        val newText = "This text contains more than ten characters and will exceed quota limit."

        sendTranslationRequest(accessToken, newText)
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.code").value("translation_quota_exceeded"))
            .andExpect(jsonPath("$.status").value(429))
    }

    @Test
    fun `expired cache entries are purged by purge use case`() {
        val now = Instant.now()
        val expiredInstant = now.minus(2, ChronoUnit.DAYS)
        val createdInstant = now.minus(32, ChronoUnit.DAYS)

        jdbcTemplate.update(
            "INSERT INTO translation.translations " +
                "(id, content_hash, source_language, target_language, content_type, translation, " +
                "provider, model_version, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            "a".repeat(64),
            "en",
            "pt-BR",
            "ANIME_DESCRIPTION",
            "Tradução antiga que expirou",
            "fake-provider",
            "fake-1.0",
            Timestamp.from(createdInstant),
            Timestamp.from(expiredInstant),
        )

        val countBefore = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM translation.translations",
            Long::class.java,
        )
        assertThat(countBefore).isEqualTo(1L)

        val purgedCount = purgeUseCase.purge(now)
        assertThat(purgedCount).isEqualTo(1)

        val countAfter = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM translation.translations",
            Long::class.java,
        )
        assertThat(countAfter).isEqualTo(0L)
    }

    private fun sendTranslationRequest(accessToken: String, text: String): ResultActions = mockMvc.perform(
        post("/api/v1/translations")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                    "text": "$text",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
                """.trimIndent(),
            ),
    )

    private fun createActiveUser(email: String, rawPassword: CharSequence): UUID {
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
            "Translator User",
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
        return userId
    }

    private fun loginSuccessfully(email: String, rawPassword: CharSequence): String {
        val result = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "email": "$email",
                        "password": "$rawPassword",
                        "clientId": "mykytadu-android"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()

        return JsonPath.read(result.response.contentAsString, "$.accessToken")
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
