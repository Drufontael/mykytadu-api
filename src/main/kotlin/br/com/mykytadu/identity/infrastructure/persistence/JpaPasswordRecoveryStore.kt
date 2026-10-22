package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.PasswordRecoveryStore
import br.com.mykytadu.identity.application.port.out.PasswordResetTokenDraft
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenType
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
@Profile("!test")
internal class JpaPasswordRecoveryStore(
    private val accounts: SpringDataUserAccountRepository,
    private val actionTokens: SpringDataActionTokenRepository,
    private val sessions: SpringDataSessionRepository,
) : PasswordRecoveryStore {

    @Transactional
    override fun createPasswordResetToken(
        email: Email,
        token: PasswordResetTokenDraft,
        invalidatedAt: Instant,
    ): String? {
        val account = accounts.findByNormalizedEmailForUpdate(email.normalized)
            ?.takeIf { it.profile.status == UserStatus.ACTIVE.persistenceValue }
            ?: return null

        actionTokens.invalidateActivePasswordResetTokens(account.id, invalidatedAt)
        actionTokens.saveAndFlush(
            ActionTokenEntity(
                id = token.id.value,
                userId = account.id,
                type = ActionTokenType.PASSWORD_RESET.persistenceValue,
                tokenHash = token.tokenHash.persistenceValue(),
                expiresAt = token.expiresAt,
                consumedAt = null,
                createdAt = token.createdAt,
            ),
        )
        return account.profile.email
    }

    @Transactional
    override fun resetPassword(tokenHash: TokenHash, passwordHash: PasswordHash, now: Instant): Boolean {
        val consumable = lockConsumableReset(tokenHash, now) ?: return false
        val updatedAccount = UserAccountPersistenceMapper.toDomain(consumable.account).resetPassword(passwordHash, now)
        UserAccountPersistenceMapper.updateCredential(consumable.account, updatedAccount)
        consumable.tokenEntity.consumedAt = consumable.tokenDomain.consume(now).consumedAt
        sessions.revokeAllForUser(consumable.account.id, now)
        accounts.flush()
        actionTokens.flush()
        return true
    }

    private fun lockConsumableReset(tokenHash: TokenHash, now: Instant): LockedReset? {
        val persistedHash = tokenHash.persistenceValue()
        val tokenType = ActionTokenType.PASSWORD_RESET.persistenceValue
        val account = actionTokens.findUserIdByTokenHashAndType(persistedHash, tokenType)
            ?.let(accounts::findByIdForUpdate)
            ?.takeIf { it.profile.status == UserStatus.ACTIVE.persistenceValue }
        return account?.let { lockedAccount ->
            actionTokens.findByTokenHashAndTypeForUpdate(persistedHash, tokenType)
                ?.takeIf {
                    it.userId == lockedAccount.id &&
                        it.tokenHash == persistedHash &&
                        it.type == tokenType
                }
                ?.let { tokenEntity ->
                    tokenEntity.toDomain()
                        .takeIf { it.isConsumableAt(now) }
                        ?.let { LockedReset(lockedAccount, tokenEntity, it) }
                }
        }
    }

    private data class LockedReset(
        val account: UserAccountEntity,
        val tokenEntity: ActionTokenEntity,
        val tokenDomain: ActionToken,
    )
}
