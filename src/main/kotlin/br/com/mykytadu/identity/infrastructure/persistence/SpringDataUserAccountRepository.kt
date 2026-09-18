package br.com.mykytadu.identity.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataUserAccountRepository : JpaRepository<UserAccountEntity, UUID> {

    fun findByProfileNormalizedEmail(normalizedEmail: String): UserAccountEntity?
}
