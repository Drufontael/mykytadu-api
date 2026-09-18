package br.com.mykytadu.identity.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserAccountTest {

    @Test
    fun `creates a pending account with normalized email and default role`() {
        val now = Instant.parse("2026-09-18T12:00:00Z")

        val account = UserAccount.pending(
            id = USER_ID,
            email = Email.from("  Person@Example.COM "),
            displayName = "  Person  ",
            passwordHash = PASSWORD_HASH,
            now = now,
        )

        assertThat(account.user.email.address).isEqualTo("Person@Example.COM")
        assertThat(account.user.email.normalized).isEqualTo("person@example.com")
        assertThat(account.user.displayName).isEqualTo("Person")
        assertThat(account.user.status).isEqualTo(UserStatus.PENDING)
        assertThat(account.user.emailVerifiedAt).isNull()
        assertThat(account.credential.algorithm).isEqualTo(PasswordAlgorithm.ARGON2ID)
        assertThat(account.roles).containsExactly(RoleAssignment(USER_ID, Role.USER))
    }

    @Test
    fun `rejects ids that are not UUIDv7`() {
        assertThatThrownBy { UserId.from(UUID.randomUUID()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("User id must be a UUIDv7")
    }

    @Test
    fun `rejects an active user without email verification`() {
        val now = Instant.parse("2026-09-18T12:00:00Z")

        assertThatThrownBy {
            User.restore(
                id = USER_ID,
                email = Email.from("person@example.com"),
                displayName = null,
                status = UserStatus.ACTIVE,
                emailVerifiedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Active user must have a verified email timestamp")
    }

    @Test
    fun `rejects a pending user with email verification`() {
        val now = Instant.parse("2026-09-18T12:00:00Z")

        assertThatThrownBy {
            User.restore(
                id = USER_ID,
                email = Email.from("person@example.com"),
                displayName = null,
                status = UserStatus.PENDING,
                emailVerifiedAt = now,
                createdAt = now,
                updatedAt = now,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Pending user must not have a verified email timestamp")
    }

    @Test
    fun `rejects user timestamps before creation`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val beforeCreation = createdAt.minusSeconds(1)

        assertThatThrownBy {
            User.restore(
                id = USER_ID,
                email = Email.from("person@example.com"),
                displayName = null,
                status = UserStatus.BLOCKED,
                emailVerifiedAt = null,
                createdAt = createdAt,
                updatedAt = beforeCreation,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("User update time must not precede creation time")

        assertThatThrownBy {
            User.restore(
                id = USER_ID,
                email = Email.from("person@example.com"),
                displayName = null,
                status = UserStatus.ACTIVE,
                emailVerifiedAt = beforeCreation,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Email verification time must not precede creation time")
    }

    @Test
    fun `rejects restored accounts without the default role`() {
        val now = Instant.parse("2026-09-18T12:00:00Z")
        val user = User.pending(USER_ID, Email.from("person@example.com"), null, now)
        val credential = PasswordCredential.argon2id(USER_ID, PASSWORD_HASH, now)

        assertThatThrownBy {
            UserAccount.restore(
                user = user,
                credential = credential,
                roles = setOf(RoleAssignment(USER_ID, Role.ADMIN)),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("User must have the default USER role")
    }

    @Test
    fun `rejects credentials and roles owned by another user`() {
        val now = Instant.parse("2026-09-18T12:00:00Z")
        val otherUserId = UserId.from(UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a902"))
        val user = User.pending(USER_ID, Email.from("person@example.com"), null, now)

        assertThatThrownBy {
            UserAccount.restore(
                user = user,
                credential = PasswordCredential.argon2id(otherUserId, PASSWORD_HASH, now),
                roles = setOf(RoleAssignment.defaultFor(USER_ID)),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Credential must belong to the user")

        assertThatThrownBy {
            UserAccount.restore(
                user = user,
                credential = PasswordCredential.argon2id(USER_ID, PASSWORD_HASH, now),
                roles = setOf(
                    RoleAssignment.defaultFor(USER_ID),
                    RoleAssignment(otherUserId, Role.ADMIN),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Every role must belong to the user")
    }

    companion object {

        private val USER_ID = UserId.from(UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a901"))
        private val PASSWORD_HASH = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=3,p=1${'$'}fixture")
    }
}
