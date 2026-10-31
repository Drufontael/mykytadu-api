package br.com.mykytadu.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
internal class SecurityAuditIntegrationTests(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `deny by default blocks unmapped arbitrary paths with authentication required problem`() {
        mockMvc.perform(get("/api/v1/internal/admin/secret-data"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("authentication_required"))
            .andExpect(jsonPath("$.traceId").isNotEmpty)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `problem details sanitizes errors without exposing internal exceptions or database details`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("""{"email":"invalid-format","password":""}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("request_validation_failed"))
            .andExpect(jsonPath("$.traceId").isNotEmpty)
            .andExpect(jsonPath("$.exception").doesNotExist())
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    fun `application configuration profiles do not contain hardcoded production secrets`() {
        val loader = YamlPropertySourceLoader()
        val baseProps = loader.load("application", ClassPathResource("application.yaml")).single()
        val stagingProps = loader.load("application-staging", ClassPathResource("application-staging.yaml")).single()

        assertThat(baseProps.source.toString())
            .contains("\${MYKYTADU_JWT_PRIVATE_KEY_PEM:}")
            .contains("\${MYKYTADU_REFRESH_ACTIVE_KEY_BASE64:}")

        assertThat(stagingProps.getProperty("spring.datasource.password"))
            .isEqualTo("\${MYKYTADU_DATABASE_PASSWORD}")

        assertThat(stagingProps.source.toString())
            .doesNotContain("password: prod", "password: secret", "123456")
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
