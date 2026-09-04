package br.com.mykytadu.app.configuration

import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
class DatabaseConfiguration {

    @Bean
    fun dataSource(environment: Environment): DataSource {
        val requiredVariables = listOf(
            "MYKYTADU_DATABASE_URL",
            "MYKYTADU_DATABASE_USERNAME",
            "MYKYTADU_DATABASE_PASSWORD",
        )
        val missingVariables = requiredVariables.filter { environment.getProperty(it).isNullOrBlank() }

        check(missingVariables.isEmpty()) {
            "Required database environment variables are missing: ${missingVariables.joinToString()}"
        }

        return DataSourceBuilder.create()
            .url(environment.getRequiredProperty("MYKYTADU_DATABASE_URL"))
            .username(environment.getRequiredProperty("MYKYTADU_DATABASE_USERNAME"))
            .password(environment.getRequiredProperty("MYKYTADU_DATABASE_PASSWORD"))
            .build()
    }
}
