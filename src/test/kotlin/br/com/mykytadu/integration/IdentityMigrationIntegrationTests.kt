package br.com.mykytadu.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.action_tokens, identity.roles, identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `creates the identity foundation with protected ownership boundaries`() {
        assertThat(PostgreSqlIntegrationFixture.tables(jdbcTemplate, "identity")).containsExactlyInAnyOrder(
            "password_credentials",
            "roles",
            "users",
            "action_tokens",
        )
        assertThat(PostgreSqlIntegrationFixture.columns(jdbcTemplate, "identity", "users"))
            .containsExactlyInAnyOrder(
                "created_at",
                "display_name",
                "email",
                "email_verified_at",
                "id",
                "normalized_email",
                "status",
                "updated_at",
            )
        assertThat(PostgreSqlIntegrationFixture.columns(jdbcTemplate, "identity", "password_credentials"))
            .containsExactlyInAnyOrder(
                "algorithm",
                "password_hash",
                "updated_at",
                "user_id",
            )
        assertThat(PostgreSqlIntegrationFixture.columns(jdbcTemplate, "identity", "roles"))
            .containsExactlyInAnyOrder("role", "user_id")
        assertThat(PostgreSqlIntegrationFixture.columns(jdbcTemplate, "identity", "action_tokens"))
            .containsExactlyInAnyOrder(
                "consumed_at",
                "created_at",
                "expires_at",
                "id",
                "token_hash",
                "type",
                "user_id",
            )
        assertThat(PostgreSqlIntegrationFixture.foreignKeyTables(jdbcTemplate, "identity"))
            .containsExactlyInAnyOrder(
                "action_tokens",
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

    @Test
    fun `protects action token purpose hash and temporal invariants`() {
        insertUser(
            id = "0199204a-1200-7001-8000-000000000001",
            email = "action-token@example.test",
            normalizedEmail = "action-token@example.test",
            status = "pending",
        )

        assertActionTokenRejected(type = "unknown", tokenHash = "a".repeat(64), expiresAt = "2026-09-20T12:00:00Z")
        assertActionTokenRejected(
            type = "email_verification",
            tokenHash = "raw-token",
            expiresAt = "2026-09-20T12:00:00Z",
        )
        assertActionTokenRejected(
            type = "email_verification",
            tokenHash = "b".repeat(64),
            expiresAt = "2026-09-19T12:00:00Z",
        )
    }

    private fun assertActionTokenRejected(type: String, tokenHash: String, expiresAt: String) {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                    INSERT INTO identity.action_tokens(
                        id, user_id, type, token_hash, expires_at, consumed_at, created_at
                    )
                    VALUES (
                        '0199204a-1200-7001-8000-000000000010',
                        '0199204a-1200-7001-8000-000000000001',
                        '$type', '$tokenHash', TIMESTAMPTZ '$expiresAt', NULL,
                        TIMESTAMPTZ '2026-09-19T12:00:00Z'
                    )
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

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSqlIntegrationFixture.newContainer()
    }
}
