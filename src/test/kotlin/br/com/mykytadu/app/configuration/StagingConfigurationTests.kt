package br.com.mykytadu.app.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class StagingConfigurationTests {

    @Test
    fun `staging profile uses external placeholders and disables local compose`() {
        val loader = YamlPropertySourceLoader()
        val properties = loader
            .load("application-staging", ClassPathResource("application-staging.yaml"))
            .single()
        val baseProperties = loader
            .load("application", ClassPathResource("application.yaml"))
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
        assertThat(properties.getProperty("mykytadu.identity.authentication.allowed-web-origins"))
            .isEqualTo("\${MYKYTADU_WEB_ALLOWED_ORIGINS}")
        assertThat(properties.getProperty("mykytadu.identity.jwt.issuer"))
            .isEqualTo("\${MYKYTADU_JWT_ISSUER}")
        assertThat(baseProperties.getProperty("mykytadu.identity.jwt.active-key-id"))
            .isEqualTo("\${MYKYTADU_JWT_ACTIVE_KEY_ID:}")
        assertThat(baseProperties.getProperty("mykytadu.identity.jwt.private-key-pem"))
            .isEqualTo("\${MYKYTADU_JWT_PRIVATE_KEY_PEM:}")
        assertThat(baseProperties.getProperty("mykytadu.identity.jwt.public-key-pem"))
            .isEqualTo("\${MYKYTADU_JWT_PUBLIC_KEY_PEM:}")
        assertThat(baseProperties.getProperty("mykytadu.identity.jwt.key-mode"))
            .isEqualTo("configured")
        assertThat(properties.source.toString())
            .doesNotContain("mykytadu-local", "localhost", "password: <")
    }
}
