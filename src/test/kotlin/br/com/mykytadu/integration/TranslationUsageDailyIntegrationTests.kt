package br.com.mykytadu.integration

import br.com.mykytadu.translation.application.port.TranslationUsageTracker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class TranslationUsageDailyIntegrationTests(@Autowired private val usageTracker: TranslationUsageTracker) {

    @Test
    fun `records and accumulates daily usage atomically`() {
        val userId = UUID.randomUUID()
        val today = LocalDate.of(2026, 10, 23)

        assertThat(usageTracker.getDailyCharactersCount(userId, today)).isEqualTo(0L)

        usageTracker.recordUsage(userId, today, 150)
        assertThat(usageTracker.getDailyCharactersCount(userId, today)).isEqualTo(150L)

        usageTracker.recordUsage(userId, today, 250)
        assertThat(usageTracker.getDailyCharactersCount(userId, today)).isEqualTo(400L)

        // Different date has zero usage
        val tomorrow = today.plusDays(1)
        assertThat(usageTracker.getDailyCharactersCount(userId, tomorrow)).isEqualTo(0L)
    }

    @Test
    fun `handles concurrent increments safely without race conditions`() {
        val userId = UUID.randomUUID()
        val date = LocalDate.of(2026, 10, 24)
        val threads = 8
        val incrementsPerThread = 5
        val charsPerIncrement = 20

        val executor = Executors.newFixedThreadPool(threads)
        val tasks = (1..(threads * incrementsPerThread)).map {
            Callable {
                usageTracker.recordUsage(userId, date, charsPerIncrement)
            }
        }

        val futures = executor.invokeAll(tasks)
        futures.forEach { it.get() }
        executor.shutdown()

        val expectedTotal = (threads * incrementsPerThread * charsPerIncrement).toLong()
        val actualTotal = usageTracker.getDailyCharactersCount(userId, date)

        assertThat(actualTotal).isEqualTo(expectedTotal)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSqlIntegrationFixture.newContainer()
    }
}
