package br.com.mykytadu.integration

import br.com.mykytadu.translation.application.PurgeExpiredTranslationsUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
internal class PrivacyAndRetentionIntegrationTests(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val purgeUseCase: PurgeExpiredTranslationsUseCase,
) {

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.update("TRUNCATE TABLE translation.translations CASCADE")
        jdbcTemplate.update("TRUNCATE TABLE identity.sessions, identity.users CASCADE")
    }

    @Test
    fun `translation table schema strictly adheres to ADR-008 without storing original text or user id`() {
        val columnNames = jdbcTemplate.query(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'translation' AND table_name = 'translations'",
        ) { rs, _ -> rs.getString("column_name") }.toSet()

        assertThat(columnNames)
            .contains(
                "id",
                "content_hash",
                "source_language",
                "target_language",
                "content_type",
                "translation",
                "provider",
                "model_version",
                "created_at",
                "expires_at",
            )

        assertThat(columnNames)
            .doesNotContain(
                "original_text",
                "source_text",
                "input_text",
                "description",
                "user_id",
                "account_id",
            )
    }

    @Test
    fun `purge removes expired translation entries while retaining active cache entries`() {
        val now = Instant.now()
        val expiredId = UUID.randomUUID()
        val validId = UUID.randomUUID()

        // 1. Inserir tradução expirada (expires_at no passado)
        insertTranslation(
            id = expiredId,
            hash = "1".repeat(64),
            createdAt = now.minus(32, ChronoUnit.DAYS),
            expiresAt = now.minus(2, ChronoUnit.DAYS),
        )

        // 2. Inserir tradução válida (expires_at no futuro)
        insertTranslation(
            id = validId,
            hash = "2".repeat(64),
            createdAt = now.minus(1, ChronoUnit.DAYS),
            expiresAt = now.plus(29, ChronoUnit.DAYS),
        )

        val beforeCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM translation.translations",
            Long::class.java,
        )
        assertThat(beforeCount).isEqualTo(2L)

        // 3. Executar purga de retenção
        val purgedCount = purgeUseCase.purge(now)
        assertThat(purgedCount).isEqualTo(1)

        // 4. Confirmar que apenas a entrada válida foi mantida
        val remainingIds = jdbcTemplate.query(
            "SELECT id FROM translation.translations",
        ) { rs, _ -> rs.getObject("id", UUID::class.java) }

        assertThat(remainingIds).containsExactly(validId)
    }

    @Test
    fun `sessions table isolates refresh token as hash and supports revocation timestamps`() {
        val columnNames = jdbcTemplate.query(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_schema = 'identity' AND table_name = 'sessions'",
        ) { rs, _ -> rs.getString("column_name") }.toSet()

        assertThat(columnNames)
            .contains("refresh_token_hash", "revoked_at", "revoke_reason", "expires_at")
            .doesNotContain("refresh_token", "raw_token", "plain_token")
    }

    private fun insertTranslation(id: UUID, hash: String, createdAt: Instant, expiresAt: Instant) {
        jdbcTemplate.update(
            "INSERT INTO translation.translations " +
                "(id, content_hash, source_language, target_language, content_type, translation, " +
                "provider, model_version, created_at, expires_at) " +
                "VALUES (?, ?, 'en', 'pt-BR', 'PLAIN_TEXT', 'Texto traduzido', 'libretranslate', 'argos-1.9', ?, ?)",
            id,
            hash,
            Timestamp.from(createdAt),
            Timestamp.from(expiresAt),
        )
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("mykytadu_test")
            .withUsername("mykytadu_test")
            .withPassword("mykytadu_test")
    }
}
