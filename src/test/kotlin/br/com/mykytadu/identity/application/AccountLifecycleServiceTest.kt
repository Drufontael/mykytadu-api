package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AccountLifecycleOutcome
import br.com.mykytadu.identity.api.BlockAccountCommand
import br.com.mykytadu.identity.api.DeleteAccountCommand
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AccountLifecycleServiceTest {

    private val accounts = mockk<UserAccountRepository>()
    private val sessions = mockk<SessionStore>()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val service = AccountLifecycleService(accounts, sessions, clock)

    @Test
    fun `blocks an active account under user lock and revokes sessions`() {
        val account = activeAccount()
        every { accounts.findByIdForUpdate(USER_ID) } returns account
        every { accounts.save(any()) } answers { firstArg() }
        every { sessions.revokeAllForInactiveUser(USER_ID, NOW) } returns 1

        val outcome = service.block(BlockAccountCommand(USER_ID.value))

        assertThat(outcome).isEqualTo(AccountLifecycleOutcome.Blocked(USER_ID.value))
        verify(exactly = 1) {
            accounts.findByIdForUpdate(USER_ID)
            accounts.save(match { it.user.status == UserStatus.BLOCKED && it.user.updatedAt == NOW })
            sessions.revokeAllForInactiveUser(USER_ID, NOW)
        }
    }

    @Test
    fun `deletes an active account under user lock and revokes sessions`() {
        val account = activeAccount()
        every { accounts.findByIdForUpdate(USER_ID) } returns account
        every { accounts.save(any()) } answers { firstArg() }
        every { sessions.revokeAllForInactiveUser(USER_ID, NOW) } returns 1

        val outcome = service.delete(DeleteAccountCommand(USER_ID.value))

        assertThat(outcome).isEqualTo(AccountLifecycleOutcome.Deleted(USER_ID.value))
        verify(exactly = 1) {
            accounts.findByIdForUpdate(USER_ID)
            accounts.save(match { it.user.status == UserStatus.DELETED && it.user.updatedAt == NOW })
            sessions.revokeAllForInactiveUser(USER_ID, NOW)
        }
    }

    @Test
    fun `returns NotFound when user does not exist on block or delete`() {
        every { accounts.findByIdForUpdate(USER_ID) } returns null

        assertThat(service.block(BlockAccountCommand(USER_ID.value)))
            .isEqualTo(AccountLifecycleOutcome.NotFound)
        assertThat(service.delete(DeleteAccountCommand(USER_ID.value)))
            .isEqualTo(AccountLifecycleOutcome.NotFound)
    }

    @Test
    fun `returns AlreadyDeleted when blocking an already deleted account`() {
        val deleted = activeAccount().delete(NOW.minusSeconds(60))
        every { accounts.findByIdForUpdate(USER_ID) } returns deleted

        val outcome = service.block(BlockAccountCommand(USER_ID.value))

        assertThat(outcome).isEqualTo(AccountLifecycleOutcome.AlreadyDeleted)
        verify(exactly = 0) {
            accounts.save(any())
            sessions.revokeAllForInactiveUser(any(), any())
        }
    }

    @Test
    fun `rejects invalid UUID format gracefully`() {
        val invalidUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
        assertThat(service.block(BlockAccountCommand(invalidUuid)))
            .isEqualTo(AccountLifecycleOutcome.NotFound)
        assertThat(service.delete(DeleteAccountCommand(invalidUuid)))
            .isEqualTo(AccountLifecycleOutcome.NotFound)
    }

    private fun activeAccount(): UserAccount {
        val createdAt = NOW.minusSeconds(3600)
        return UserAccount.pending(
            id = USER_ID,
            email = Email.from("active@example.com"),
            displayName = "Active",
            passwordHash = PasswordHash.from($$"$argon2id$v=19$m=16,t=2,p=1$mock$mock"),
            now = createdAt,
        ).verifyEmail(createdAt.plusSeconds(60))
    }

    companion object {
        private val USER_ID = UserId.from(UUID.fromString("0199204a-1200-7001-8000-000000000001"))
        private val NOW = Instant.parse("2026-10-22T10:00:00Z")
    }
}
