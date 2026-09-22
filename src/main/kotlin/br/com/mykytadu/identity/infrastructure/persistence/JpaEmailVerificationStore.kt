package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.EmailVerificationStore
import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.ActionTokenType
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
@Profile("!test")
internal class JpaEmailVerificationStore(
    private val accounts: SpringDataUserAccountRepository,
    private val actionTokens: SpringDataActionTokenRepository,
) : EmailVerificationStore {

    @Transactional
    override fun verify(tokenHash: TokenHash, verifiedAt: Instant): Boolean {
        val verification = lockConsumableVerification(tokenHash, verifiedAt)
        return if (verification == null) {
            false
        } else {
            val verifiedAccount = UserAccountPersistenceMapper.toDomain(verification.account).verifyEmail(verifiedAt)

            UserAccountPersistenceMapper.updateProfile(verification.account, verifiedAccount)
            verification.token.consumedAt = verification.tokenDomain.consume(verifiedAt).consumedAt
            accounts.flush()
            actionTokens.flush()
            true
        }
    }

    private fun lockConsumableVerification(tokenHash: TokenHash, verifiedAt: Instant): LockedVerification? {
        val persistedHash = tokenHash.persistenceValue()
        val tokenType = ActionTokenType.EMAIL_VERIFICATION.persistenceValue
        val account = actionTokens.findUserIdByTokenHashAndType(persistedHash, tokenType)
            ?.let(accounts::findByIdForUpdate)
            ?.takeIf { it.profile.status == UserStatus.PENDING.persistenceValue }
        val verification = account?.let { lockedAccount ->
            actionTokens.findByTokenHashAndTypeForUpdate(persistedHash, tokenType)
                ?.takeIf {
                    it.userId == lockedAccount.id &&
                        it.tokenHash == persistedHash &&
                        it.type == tokenType
                }
                ?.let { token ->
                    token.toDomain()
                        .takeIf { it.isConsumableAt(verifiedAt) }
                        ?.let { LockedVerification(lockedAccount, token, it) }
                }
        }
        return verification
    }

    private data class LockedVerification(
        val account: UserAccountEntity,
        val token: ActionTokenEntity,
        val tokenDomain: ActionToken,
    )

    private fun ActionTokenEntity.toDomain(): ActionToken = ActionToken.restore(
        id = ActionTokenId.from(id),
        userId = UserId.from(userId),
        type = ActionTokenType.fromPersistenceValue(type),
        tokenHash = TokenHash.sha256(tokenHash),
        expiresAt = expiresAt,
        consumedAt = consumedAt,
        createdAt = createdAt,
    )
}
