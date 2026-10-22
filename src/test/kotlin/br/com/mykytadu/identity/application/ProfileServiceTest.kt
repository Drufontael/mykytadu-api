package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.GetProfileQuery
import br.com.mykytadu.identity.api.ProfileOutcome
import br.com.mykytadu.identity.api.UpdateProfileCommand
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.Role
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProfileServiceTest {

    private val now = Instant.parse("2026-09-18T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `returns profile for active user`() {
        val account = createAccount(UserStatus.ACTIVE)
        val repository = InMemoryAccountRepository(account)
        val service = ProfileService(repository, clock)

        val outcome = service.getProfile(GetProfileQuery(account.user.id.value))

        assertThat(outcome).isInstanceOf(ProfileOutcome.Success::class.java)
        val profile = (outcome as ProfileOutcome.Success).profile
        assertThat(profile.id).isEqualTo(account.user.id.value)
        assertThat(profile.email).isEqualTo("person@example.com")
        assertThat(profile.displayName).isEqualTo("Person")
        assertThat(profile.status).isEqualTo("active")
        assertThat(profile.roles).containsExactly("USER")
    }

    @Test
    fun `returns not found when account does not exist on query`() {
        val repository = InMemoryAccountRepository(null)
        val service = ProfileService(repository, clock)

        val nonExistentV7 = UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a999")
        assertThat(service.getProfile(GetProfileQuery(nonExistentV7))).isEqualTo(ProfileOutcome.NotFound)
        assertThat(service.getProfile(GetProfileQuery(UUID.randomUUID()))).isEqualTo(ProfileOutcome.NotFound)
    }

    @Test
    fun `returns inactive when user is not active on query`() {
        val account = createAccount(UserStatus.BLOCKED)
        val repository = InMemoryAccountRepository(account)
        val service = ProfileService(repository, clock)

        val outcome = service.getProfile(GetProfileQuery(account.user.id.value))

        assertThat(outcome).isEqualTo(ProfileOutcome.Inactive)
    }

    @Test
    fun `updates display name and persists for active user`() {
        val account = createAccount(UserStatus.ACTIVE)
        val repository = InMemoryAccountRepository(account)
        val updateInstant = now.plusSeconds(300)
        val updateClock = Clock.fixed(updateInstant, ZoneOffset.UTC)
        val service = ProfileService(repository, updateClock)

        val outcome = service.updateProfile(UpdateProfileCommand(account.user.id.value, "New Name"))

        assertThat(outcome).isInstanceOf(ProfileOutcome.Success::class.java)
        val profile = (outcome as ProfileOutcome.Success).profile
        assertThat(profile.displayName).isEqualTo("New Name")
        assertThat(profile.updatedAt).isEqualTo(updateInstant)
        assertThat(repository.savedAccount?.user?.displayName).isEqualTo("New Name")
    }

    @Test
    fun `returns not found when account does not exist on update`() {
        val repository = InMemoryAccountRepository(null)
        val service = ProfileService(repository, clock)

        val nonExistentV7 = UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a999")
        assertThat(service.updateProfile(UpdateProfileCommand(nonExistentV7, "New Name")))
            .isEqualTo(ProfileOutcome.NotFound)
        assertThat(service.updateProfile(UpdateProfileCommand(UUID.randomUUID(), "New Name")))
            .isEqualTo(ProfileOutcome.NotFound)
    }

    @Test
    fun `returns inactive when user is not active on update`() {
        val account = createAccount(UserStatus.BLOCKED)
        val repository = InMemoryAccountRepository(account)
        val service = ProfileService(repository, clock)

        val outcome = service.updateProfile(UpdateProfileCommand(account.user.id.value, "New Name"))

        assertThat(outcome).isEqualTo(ProfileOutcome.Inactive)
    }

    private fun createAccount(status: UserStatus): UserAccount {
        val userId = UserId.from(UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a901"))
        val user = User.restore(
            id = userId,
            email = Email.from("person@example.com"),
            displayName = "Person",
            status = status,
            emailVerifiedAt = if (status == UserStatus.PENDING) null else now,
            createdAt = now,
            updatedAt = now,
        )
        val credential = PasswordCredential.argon2id(
            userId = userId,
            hash = PasswordHash.from($$"$argon2id$v=19$m=65536,t=3,p=1$fixture"),
            now = now,
        )
        return UserAccount.restore(user, credential, setOf(RoleAssignment(userId, Role.USER)))
    }

    private class InMemoryAccountRepository(var account: UserAccount?) : UserAccountRepository {
        var savedAccount: UserAccount? = null

        override fun save(account: UserAccount): UserAccount {
            this.account = account
            this.savedAccount = account
            return account
        }

        override fun findByEmail(email: Email): UserAccount? = account.takeIf { it?.user?.email == email }

        override fun findById(userId: UserId): UserAccount? = account.takeIf { it?.user?.id == userId }

        override fun findByIdForUpdate(userId: UserId): UserAccount? = findById(userId)
    }
}
