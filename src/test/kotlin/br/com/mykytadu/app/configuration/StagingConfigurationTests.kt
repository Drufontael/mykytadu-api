package br.com.mykytadu.app.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class StagingConfigurationTests {

    @Test
    fun `staging profile uses external placeholders and disables local compose`() {
        val properties = YamlPropertySourceLoader()
            .load("application-staging", ClassPathResource("application-staging.yaml"))
            .single()

        assertThat(properties.getProperty("spring.config.activate.on-profile")).isEqualTo("staging")
        assertThat(properties.getProperty("spring.docker.compose.enabled")).isEqualTo(false)
        assertThat(properties.getProperty("spring.datasource.url"))
            .isEqualTo("\${MYKYTADU_DATABASE_URL}")
        assertThat(properties.getProperty("spring.datasource.username"))
            .isEqualTo("\${MYKYTADU_DATABASE_USERNAME}")
        assertThat(properties.getProperty("spring.datasource.password"))
            .isEqualTo("\${MYKYTADU_DATABASE_PASSWORD}")
        assertThat(properties.getProperty("server.port"))
            .isEqualTo("\${MYKYTADU_SERVER_PORT:8081}")
        assertThat(properties.source.toString())
            .doesNotContain("mykytadu-local", "localhost", "password: <")
    }
}
