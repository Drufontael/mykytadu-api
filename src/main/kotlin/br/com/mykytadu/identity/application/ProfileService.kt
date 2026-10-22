package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.GetProfileQuery
import br.com.mykytadu.identity.api.IdentityProfile
import br.com.mykytadu.identity.api.ProfileOutcome
import br.com.mykytadu.identity.api.UpdateProfileCommand
import br.com.mykytadu.identity.api.UserProfileDto
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
internal class ProfileService(private val accounts: UserAccountRepository, private val clock: Clock) :
    IdentityProfile {

    @Transactional(readOnly = true)
    override fun getProfile(query: GetProfileQuery): ProfileOutcome =
        when (val lookup = findActiveAccount(query.userId, forUpdate = false)) {
            is AccountLookupOutcome.Active -> ProfileOutcome.Success(lookup.account.toDto())
            AccountLookupOutcome.NotFound -> ProfileOutcome.NotFound
            AccountLookupOutcome.Inactive -> ProfileOutcome.Inactive
        }

    @Transactional
    override fun updateProfile(command: UpdateProfileCommand): ProfileOutcome =
        when (val lookup = findActiveAccount(command.userId, forUpdate = true)) {
            is AccountLookupOutcome.Active -> {
                val updated = lookup.account.updateDisplayName(command.displayName, clock.instant())
                accounts.save(updated)
                ProfileOutcome.Success(updated.toDto())
            }

            AccountLookupOutcome.NotFound -> ProfileOutcome.NotFound

            AccountLookupOutcome.Inactive -> ProfileOutcome.Inactive
        }

    private fun findActiveAccount(userId: UUID, forUpdate: Boolean): AccountLookupOutcome {
        val account = loadAccount(userId, forUpdate) ?: return AccountLookupOutcome.NotFound
        return if (account.user.status == UserStatus.ACTIVE) {
            AccountLookupOutcome.Active(account)
        } else {
            AccountLookupOutcome.Inactive
        }
    }

    private fun loadAccount(userId: UUID, forUpdate: Boolean): UserAccount? {
        val parsedId = runCatching { UserId.from(userId) }.getOrNull() ?: return null
        return if (forUpdate) accounts.findByIdForUpdate(parsedId) else accounts.findById(parsedId)
    }

    private sealed interface AccountLookupOutcome {
        data class Active(val account: UserAccount) : AccountLookupOutcome
        data object NotFound : AccountLookupOutcome
        data object Inactive : AccountLookupOutcome
    }

    private fun UserAccount.toDto(): UserProfileDto = UserProfileDto(
        id = user.id.value,
        email = user.email.address,
        displayName = user.displayName,
        status = user.status.persistenceValue,
        roles = roles.map { it.role.name }.toSet(),
        emailVerifiedAt = user.emailVerifiedAt,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}
