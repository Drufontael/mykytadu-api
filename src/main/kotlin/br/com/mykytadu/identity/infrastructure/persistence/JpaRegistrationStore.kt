package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.RegistrationConflictException
import br.com.mykytadu.identity.application.port.out.RegistrationStore
import br.com.mykytadu.identity.application.port.out.VerificationTokenDraft
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenType
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserStatus
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
@Profile("!test")
internal class JpaRegistrationStore(
    private val accounts: SpringDataUserAccountRepository,
    private val actionTokens: SpringDataActionTokenRepository,
) : RegistrationStore {

    @Transactional
    override fun create(account: UserAccount, verificationToken: ActionToken) {
        try {
            accounts.saveAndFlush(UserAccountPersistenceMapper.toEntity(account))
            actionTokens.saveAndFlush(verificationToken.toEntity())
        } catch (exception: DataIntegrityViolationException) {
            throw RegistrationConflictException(exception)
        }
    }

    @Transactional
    override fun replaceVerificationToken(
        email: Email,
        token: VerificationTokenDraft,
        invalidatedAt: Instant,
    ): String? {
        val account = accounts.findByNormalizedEmailForUpdate(email.normalized)
            ?.takeIf { it.profile.status == UserStatus.PENDING.persistenceValue }
            ?: return null

        actionTokens.invalidateActiveVerificationTokens(account.id, invalidatedAt)
        actionTokens.saveAndFlush(
            ActionTokenEntity(
                id = token.id.value,
                userId = account.id,
                type = ActionTokenType.EMAIL_VERIFICATION.persistenceValue,
                tokenHash = token.tokenHash.persistenceValue(),
                expiresAt = token.expiresAt,
                consumedAt = null,
                createdAt = token.createdAt,
            ),
        )
        return account.profile.email
    }

    private fun ActionToken.toEntity(): ActionTokenEntity = ActionTokenEntity(
        id = id.value,
        userId = userId.value,
        type = type.persistenceValue,
        tokenHash = tokenHash.persistenceValue(),
        expiresAt = expiresAt,
        consumedAt = consumedAt,
        createdAt = createdAt,
    )
}
