package br.com.mykytadu.translation.application.port

import java.time.LocalDate
import java.util.UUID

interface TranslationUsageTracker {
    fun getDailyCharactersCount(userId: UUID, date: LocalDate): Long
    fun recordUsage(userId: UUID, date: LocalDate, characters: Int)
}
