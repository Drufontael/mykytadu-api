package br.com.mykytadu.translation.infrastructure.fake

import br.com.mykytadu.translation.application.port.TranslationUsageTracker
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryTranslationUsageTracker : TranslationUsageTracker {

    private val usageMap = ConcurrentHashMap<Pair<UUID, LocalDate>, AtomicLong>()

    override fun getDailyCharactersCount(userId: UUID, date: LocalDate): Long =
        usageMap[Pair(userId, date)]?.get() ?: 0L

    override fun recordUsage(userId: UUID, date: LocalDate, characters: Int) {
        usageMap.computeIfAbsent(Pair(userId, date)) { AtomicLong(0) }.addAndGet(characters.toLong())
    }

    fun clear() {
        usageMap.clear()
    }
}
