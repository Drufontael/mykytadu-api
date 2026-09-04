package br.com.mykytadu.app.configuration

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

class DatabaseConfigurationTest {

    @Test
    fun `reports every missing database environment variable`() {
        val environment = mockk<Environment>()
        every { environment.getProperty("MYKYTADU_DATABASE_URL") } returns null
        every { environment.getProperty("MYKYTADU_DATABASE_USERNAME") } returns null
        every { environment.getProperty("MYKYTADU_DATABASE_PASSWORD") } returns null

        assertThatThrownBy { DatabaseConfiguration().dataSource(environment) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("MYKYTADU_DATABASE_URL")
            .hasMessageContaining("MYKYTADU_DATABASE_USERNAME")
            .hasMessageContaining("MYKYTADU_DATABASE_PASSWORD")

        verify(exactly = 1) { environment.getProperty("MYKYTADU_DATABASE_URL") }
        verify(exactly = 1) { environment.getProperty("MYKYTADU_DATABASE_USERNAME") }
        verify(exactly = 1) { environment.getProperty("MYKYTADU_DATABASE_PASSWORD") }
    }
}
