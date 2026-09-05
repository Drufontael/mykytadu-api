package br.com.mykytadu.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class DatabaseMigrationIntegrationTests(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `starts an ephemeral PostgreSQL and applies every migration`() {
        assertThat(postgres.isRunning).isTrue()
        assertThat(postgres.dockerImageName).isEqualTo("postgres:18.6-trixie")
        assertThat(currentDatabase()).isEqualTo("test")
        assertThat(domainSchemas()).containsExactlyInAnyOrder("identity", "translation")
        assertThat(successfulMigrations()).isOne()
        assertThat(businessTablesInPublicSchema()).isZero()

        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
        postgres.stop()
        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isServiceUnavailable() } }
    }

    private fun domainSchemas(): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name IN ('identity', 'translation')
        """.trimIndent(),
        String::class.java,
    ).filterNotNull().toSet()

    private fun currentDatabase(): String? =
        jdbcTemplate.queryForObject("SELECT current_database()", String::class.java)

    private fun successfulMigrations(): Int = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM flyway_schema_history WHERE success",
        Int::class.java,
    ) ?: 0

    private fun businessTablesInPublicSchema(): Int = jdbcTemplate.queryForObject(
        """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name <> 'flyway_schema_history'
        """.trimIndent(),
        Int::class.java,
    ) ?: 0

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-trixie")
    }
}
