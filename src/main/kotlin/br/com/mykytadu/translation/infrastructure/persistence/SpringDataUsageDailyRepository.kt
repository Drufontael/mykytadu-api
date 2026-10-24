package br.com.mykytadu.translation.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal interface SpringDataUsageDailyRepository : JpaRepository<UsageDailyEntity, UUID> {

    @Query(
        """
        SELECT COALESCE(u.charactersCount, 0)
        FROM UsageDailyEntity u
        WHERE u.userId = :userId AND u.usageDate = :usageDate
        """,
    )
    fun findCharactersCount(@Param("userId") userId: UUID, @Param("usageDate") usageDate: LocalDate): Long?

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO translation.usage_daily (
                id, user_id, usage_date, characters_count, requests_count, created_at, updated_at
            ) VALUES (
                :id, :userId, :usageDate, :characters, 1, :now, :now
            )
            ON CONFLICT (user_id, usage_date)
            DO UPDATE SET
                characters_count = translation.usage_daily.characters_count + EXCLUDED.characters_count,
                requests_count = translation.usage_daily.requests_count + 1,
                updated_at = EXCLUDED.updated_at
        """,
    )
    @Suppress("LongParameterList")
    fun recordUsage(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("usageDate") usageDate: LocalDate,
        @Param("characters") characters: Int,
        @Param("now") now: Instant,
    ): Int
}
