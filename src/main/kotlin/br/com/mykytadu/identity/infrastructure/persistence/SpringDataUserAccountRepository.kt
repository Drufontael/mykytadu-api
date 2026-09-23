package br.com.mykytadu.identity.infrastructure.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataUserAccountRepository : JpaRepository<UserAccountEntity, UUID> {

    fun findByProfileNormalizedEmail(normalizedEmail: String): UserAccountEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccountEntity account where account.profile.normalizedEmail = :normalizedEmail")
    fun findByNormalizedEmailForUpdate(@Param("normalizedEmail") normalizedEmail: String): UserAccountEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccountEntity account where account.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): UserAccountEntity?
}
