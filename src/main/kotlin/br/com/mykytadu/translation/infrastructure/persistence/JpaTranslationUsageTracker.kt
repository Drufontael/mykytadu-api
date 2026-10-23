package br.com.mykytadu.translation.infrastructure.persistence

import br.com.mykytadu.translation.application.port.TranslationIdGenerator
import br.com.mykytadu.translation.application.port.TranslationUsageTracker
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Component
internal class JpaTranslationUsageTracker(
    private val repository: SpringDataUsageDailyRepository,
    private val idGenerator: TranslationIdGenerator,
    private val clock: Clock,
) : TranslationUsageTracker {

    @Transactional(readOnly = true)
    override fun getDailyCharactersCount(userId: UUID, date: LocalDate): Long =
        repository.findCharactersCount(userId, date) ?: 0L

    @Transactional
    override fun recordUsage(userId: UUID, date: LocalDate, characters: Int) {
        val now = clock.instant()
        repository.recordUsage(
            id = idGenerator.nextId(),
            userId = userId,
            usageDate = date,
            characters = characters,
            now = now,
        )
    }
}
