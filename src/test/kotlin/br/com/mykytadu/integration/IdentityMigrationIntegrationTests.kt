package br.com.mykytadu.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class IdentityMigrationIntegrationTests(@Autowired private val jdbcTemplate: JdbcTemplate) {

    @Test
    fun `creates the identity foundation with protected ownership boundaries`() {
        assertThat(identityTables()).containsExactlyInAnyOrder(
            "password_credentials",
            "roles",
            "users",
        )
        assertThat(tableColumns("users")).containsExactlyInAnyOrder(
            "created_at",
            "display_name",
            "email",
            "email_verified_at",
            "id",
            "normalized_email",
            "status",
            "updated_at",
        )
        assertThat(tableColumns("password_credentials")).containsExactlyInAnyOrder(
            "algorithm",
            "password_hash",
            "updated_at",
            "user_id",
        )
        assertThat(tableColumns("roles")).containsExactlyInAnyOrder("role", "user_id")
        assertThat(foreignKeyTables()).containsExactlyInAnyOrder(
            "password_credentials",
            "roles",
        )
    }

    @Test
    fun `protects normalized email status algorithm and role invariants`() {
        insertUser(
            id = "01991f18-7d42-7b21-a2ef-1d8e6e14a901",
            email = "identity-duplicate@example.test",
            normalizedEmail = "identity-duplicate@example.test",
            status = "pending",
        )

        assertThatThrownBy {
            insertUser(
                id = "01991f18-7d42-7b21-a2ef-1d8e6e14a902",
                email = "identity-other@example.test",
                normalizedEmail = "identity-duplicate@example.test",
                status = "pending",
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            insertUser(
                id = "01991f18-7d42-7b21-a2ef-1d8e6e14a903",
                email = "identity-invalid-status@example.test",
                normalizedEmail = "identity-invalid-status@example.test",
                status = "unknown",
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                    INSERT INTO identity.password_credentials(user_id, password_hash, algorithm, updated_at)
                    VALUES ('01991f18-7d42-7b21-a2ef-1d8e6e14a901', 'fixture', 'unknown', TIMESTAMPTZ '2026-09-17T12:00:00Z')
                """.trimIndent(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                    INSERT INTO identity.roles(user_id, role)
                    VALUES ('01991f18-7d42-7b21-a2ef-1d8e6e14a901', 'OWNER')
                """.trimIndent(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun insertUser(id: String, email: String, normalizedEmail: String, status: String) {
        jdbcTemplate.update(
            """
                INSERT INTO identity.users(
                    id, email, normalized_email, display_name, status,
                    email_verified_at, created_at, updated_at
                )
                VALUES (
                    '$id', '$email', '$normalizedEmail', NULL, '$status',
                    NULL, TIMESTAMPTZ '2026-09-17T12:00:00Z', TIMESTAMPTZ '2026-09-17T12:00:00Z'
                )
            """.trimIndent(),
        )
    }

    private fun identityTables(): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'identity'
              AND table_type = 'BASE TABLE'
        """.trimIndent(),
        String::class.java,
    ).filterNotNull().toSet()

    private fun tableColumns(table: String): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'identity'
              AND table_name = '$table'
        """.trimIndent(),
        String::class.java,
    ).filterNotNull().toSet()

    private fun foreignKeyTables(): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT table_name
            FROM information_schema.table_constraints
            WHERE constraint_schema = 'identity'
              AND constraint_type = 'FOREIGN KEY'
        """.trimIndent(),
        String::class.java,
    ).filterNotNull().toSet()

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-trixie")
    }
}
