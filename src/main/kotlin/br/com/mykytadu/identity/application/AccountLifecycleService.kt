package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AccountLifecycleOutcome
import br.com.mykytadu.identity.api.BlockAccountCommand
import br.com.mykytadu.identity.api.DeleteAccountCommand
import br.com.mykytadu.identity.api.IdentityAccountLifecycle
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@Profile("!test")
class AccountLifecycleService(
    private val accounts: UserAccountRepository,
    private val sessions: SessionStore,
    private val clock: Clock,
) : IdentityAccountLifecycle {

    @Transactional
    override fun block(command: BlockAccountCommand): AccountLifecycleOutcome {
        val target = findTargetAccount(command.userId) ?: return AccountLifecycleOutcome.NotFound
        return executeBlock(target.first, target.second, command.userId)
    }

    @Transactional
    override fun delete(command: DeleteAccountCommand): AccountLifecycleOutcome {
        val target = findTargetAccount(command.userId) ?: return AccountLifecycleOutcome.NotFound
        return executeDelete(target.first, target.second, command.userId)
    }

    private fun findTargetAccount(rawId: UUID): Pair<UserAccount, UserId>? {
        val userId = parseUserId(rawId) ?: return null
        return accounts.findByIdForUpdate(userId)?.let { it to userId }
    }

    private fun executeBlock(account: UserAccount, userId: UserId, rawId: UUID): AccountLifecycleOutcome {
        if (account.user.status == UserStatus.DELETED) {
            return AccountLifecycleOutcome.AlreadyDeleted
        }
        val now = clock.instant()
        accounts.save(account.block(now))
        sessions.revokeAllForInactiveUser(userId, now)
        return AccountLifecycleOutcome.Blocked(rawId)
    }

    private fun executeDelete(account: UserAccount, userId: UserId, rawId: UUID): AccountLifecycleOutcome {
        val now = clock.instant()
        accounts.save(account.delete(now))
        sessions.revokeAllForInactiveUser(userId, now)
        return AccountLifecycleOutcome.Deleted(rawId)
    }

    private fun parseUserId(id: UUID): UserId? = runCatching { UserId.from(id) }.getOrNull()
}
