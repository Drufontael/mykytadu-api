package br.com.mykytadu.translation.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "usage_daily", schema = "translation")
internal class UsageDailyEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "usage_date", nullable = false)
    val usageDate: LocalDate,

    @Column(name = "characters_count", nullable = false)
    var charactersCount: Long,

    @Column(name = "requests_count", nullable = false)
    var requestsCount: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
