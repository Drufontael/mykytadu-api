package br.com.mykytadu.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.sql.Statement
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
internal class DatabaseBackupRestoreIntegrationTests(@Autowired private val jdbcTemplate: JdbcTemplate) {

    @Test
    fun `logical backup and restore into isolated database preserves schema and data`() {
        val testUserId = UUID.randomUUID()
        val testContentHash = "a".repeat(64)

        insertSyntheticData(testUserId, testContentHash)
        createRecoveryDatabase()

        try {
            executeDumpAndRestore()
            val recoveryJdbcUrl = postgres.jdbcUrl.replace("/${postgres.databaseName}", "/mykytadu_recovery")
            DriverManager.getConnection(recoveryJdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    verifyFlywayHistory(statement)
                    verifySchemaIsolation(statement)
                    verifyDataIntegrity(statement, testUserId, testContentHash)
                }
            }
        } finally {
            cleanupRecoveryDatabase()
        }
    }

    private fun insertSyntheticData(testUserId: UUID, testContentHash: String) {
        jdbcTemplate.update(
            "INSERT INTO identity.users (id, email, normalized_email, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'active', NOW(), NOW())",
            testUserId,
            "dr-backup-test@example.com",
            "dr-backup-test@example.com",
        )
        jdbcTemplate.update(
            "INSERT INTO translation.translations (id, content_hash, source_language, " +
                "target_language, content_type, translation, provider, model_version, " +
                "created_at, expires_at) VALUES (?, ?, 'EN', 'PT_BR', 'PLAIN_TEXT', " +
                "'Texto traduzido backup', 'libretranslate', 'argos-1.9', NOW(), NOW() + INTERVAL '30 days')",
            UUID.randomUUID(),
            testContentHash,
        )
    }

    private fun createRecoveryDatabase() {
        val createDbResult = postgres.execInContainer(
            "createdb",
            "-U",
            postgres.username,
            "mykytadu_recovery",
        )
        assertThat(createDbResult.exitCode)
            .withFailMessage("Falha ao executar createdb: ${createDbResult.stderr}")
            .isEqualTo(0)
    }

    private fun executeDumpAndRestore() {
        val dumpResult = postgres.execInContainer(
            "pg_dump",
            "-U",
            postgres.username,
            "-Fc",
            "-f",
            "/tmp/mykytadu_test_dr.dump",
            postgres.databaseName,
        )
        assertThat(dumpResult.exitCode)
            .withFailMessage("Falha ao executar pg_dump: ${dumpResult.stderr}")
            .isEqualTo(0)

        val restoreResult = postgres.execInContainer(
            "pg_restore",
            "-U",
            postgres.username,
            "--exit-on-error",
            "--no-owner",
            "--no-privileges",
            "-d",
            "mykytadu_recovery",
            "/tmp/mykytadu_test_dr.dump",
        )
        assertThat(restoreResult.exitCode)
            .withFailMessage("Falha ao executar pg_restore: ${restoreResult.stderr}")
            .isEqualTo(0)
    }

    private fun verifyFlywayHistory(statement: Statement) {
        val flywayRs = statement.executeQuery(
            "SELECT count(*) as total, count(*) FILTER (WHERE success = true) as successes " +
                "FROM flyway_schema_history",
        )
        assertThat(flywayRs.next()).isTrue()
        val totalMigrations = flywayRs.getInt("total")
        val successfulMigrations = flywayRs.getInt("successes")
        assertThat(totalMigrations).isGreaterThan(0)
        assertThat(successfulMigrations).isEqualTo(totalMigrations)
    }

    private fun verifySchemaIsolation(statement: Statement) {
        val publicTablesRs = statement.executeQuery(
            "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name != 'flyway_schema_history'",
        )
        assertThat(publicTablesRs.next()).isFalse()

        val schemasRs = statement.executeQuery(
            "SELECT DISTINCT table_schema FROM information_schema.tables " +
                "WHERE table_schema IN ('identity', 'translation')",
        )
        val schemasFound = mutableSetOf<String>()
        while (schemasRs.next()) {
            schemasFound.add(schemasRs.getString("table_schema"))
        }
        assertThat(schemasFound).containsExactlyInAnyOrder("identity", "translation")
    }

    private fun verifyDataIntegrity(statement: Statement, testUserId: UUID, testContentHash: String) {
        val userRs = statement.executeQuery(
            "SELECT count(*) as count FROM identity.users WHERE id = '$testUserId'",
        )
        assertThat(userRs.next()).isTrue()
        assertThat(userRs.getInt("count")).isEqualTo(1)

        val translationRs = statement.executeQuery(
            "SELECT count(*) as count FROM translation.translations WHERE content_hash = '$testContentHash'",
        )
        assertThat(translationRs.next()).isTrue()
        assertThat(translationRs.getInt("count")).isEqualTo(1)
    }

    private fun cleanupRecoveryDatabase() {
        postgres.execInContainer("rm", "-f", "/tmp/mykytadu_test_dr.dump")
        postgres.execInContainer("dropdb", "-U", postgres.username, "mykytadu_recovery")
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
