package br.com.mykytadu.identity.infrastructure.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

internal interface SpringDataActionTokenRepository : JpaRepository<ActionTokenEntity, UUID> {

    @Query("select token.userId from ActionTokenEntity token where token.tokenHash = :tokenHash and token.type = :type")
    fun findUserIdByTokenHashAndType(@Param("tokenHash") tokenHash: String, @Param("type") type: String): UUID?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from ActionTokenEntity token where token.tokenHash = :tokenHash and token.type = :type")
    fun findByTokenHashAndTypeForUpdate(
        @Param("tokenHash") tokenHash: String,
        @Param("type") type: String,
    ): ActionTokenEntity?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
            update ActionTokenEntity token
               set token.consumedAt = :consumedAt
             where token.userId = :userId
               and token.type = 'email_verification'
               and token.consumedAt is null
        """,
    )
    fun invalidateActiveVerificationTokens(
        @Param("userId") userId: UUID,
        @Param("consumedAt") consumedAt: Instant,
    ): Int
}
