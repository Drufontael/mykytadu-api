package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.PasswordRecoveryStore
import br.com.mykytadu.identity.application.port.out.PasswordResetTokenDraft
import br.com.mykytadu.identity.domain.model.ActionTokenType
import br.com.mykytadu.identity.domain.model.Email
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
}
