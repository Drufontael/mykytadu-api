package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

@Repository
@Profile("!test")
internal class JpaUserAccountRepository(private val delegate: SpringDataUserAccountRepository) :
    UserAccountRepository {

    override fun save(account: UserAccount): UserAccount = delegate.save(account.toEntity()).toDomain()

    override fun findByEmail(email: Email): UserAccount? =
        delegate.findByProfileNormalizedEmail(email.normalized)?.toDomain()

    private fun UserAccount.toEntity(): UserAccountEntity = UserAccountPersistenceMapper.toEntity(this)

    private fun UserAccountEntity.toDomain(): UserAccount = UserAccountPersistenceMapper.toDomain(this)
}
