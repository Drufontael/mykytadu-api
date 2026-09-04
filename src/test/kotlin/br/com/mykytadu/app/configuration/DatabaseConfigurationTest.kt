package br.com.mykytadu.app.configuration

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class DatabaseConfigurationTest {

    @Test
    fun `reports every missing database environment variable`() {
        val exception = assertFailsWith<IllegalStateException> {
            DatabaseConfiguration().dataSource(MockEnvironment())
        }

        assertContains(exception.message.orEmpty(), "MYKYTADU_DATABASE_URL")
        assertContains(exception.message.orEmpty(), "MYKYTADU_DATABASE_USERNAME")
        assertContains(exception.message.orEmpty(), "MYKYTADU_DATABASE_PASSWORD")
    }
}
