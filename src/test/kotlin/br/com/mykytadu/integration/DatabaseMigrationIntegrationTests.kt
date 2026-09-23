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
        assertThat(postgres.dockerImageName).isEqualTo(PostgreSqlIntegrationFixture.IMAGE)
        assertThat(PostgreSqlIntegrationFixture.currentDatabase(jdbcTemplate)).isEqualTo("test")
        assertThat(PostgreSqlIntegrationFixture.schemas(jdbcTemplate, setOf("identity", "translation")))
            .containsExactlyInAnyOrder("identity", "translation")
        assertThat(PostgreSqlIntegrationFixture.successfulMigrationVersions(jdbcTemplate)).containsExactlyInAnyOrder(
            "20260904184904",
            "20260917193036",
            "20260919124700",
            "20260922205855",
        )
        assertThat(PostgreSqlIntegrationFixture.businessTablesInPublicSchema(jdbcTemplate)).isZero()

        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isOk() } }
        postgres.stop()
        mockMvc.get("/actuator/health/liveness").andExpect { status { isOk() } }
        mockMvc.get("/actuator/health/readiness").andExpect { status { isServiceUnavailable() } }
    }

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSqlIntegrationFixture.newContainer()
    }
}
