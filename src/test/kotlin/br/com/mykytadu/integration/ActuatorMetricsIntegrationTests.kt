package br.com.mykytadu.integration

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
internal class ActuatorMetricsIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val meterRegistry: MeterRegistry,
) {

    @Test
    fun `health probes expose up status for liveness and readiness`() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"))
            .andExpect(jsonPath("$.status").value("UP"))

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"))
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `unauthenticated requests to actuator metrics are denied by default`() {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("authentication_required"))
    }

    @Test
    fun `meter registry contains core meters required for slo and sli monitoring`() {
        // Exercise an endpoint to generate http.server.requests telemetry
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)

        val meterNames = meterRegistry.meters.map { it.id.name }.toSet()

        assertThat(meterNames)
            .contains(
                "http.server.requests",
                "jvm.memory.used",
                "mykytadu.identity.authentication.attempts",
                "mykytadu.translation.cache",
                "mykytadu.translation.characters.translated",
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
