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
    fun `verifies a pending account at the supplied instant`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val verifiedAt = createdAt.plusSeconds(60)
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Person",
            PASSWORD_HASH,
            createdAt,
        )

        val verified = account.verifyEmail(verifiedAt)

        assertThat(verified.user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(verified.user.emailVerifiedAt).isEqualTo(verifiedAt)
        assertThat(verified.user.updatedAt).isEqualTo(verifiedAt)
        assertThat(verified.credential).isSameAs(account.credential)
        assertThat(verified.roles).isEqualTo(account.roles)
    }

    @Test
    fun `does not verify a non-pending account or move time backwards`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            null,
            PASSWORD_HASH,
            createdAt,
        )
        val active = account.verifyEmail(createdAt.plusSeconds(60))

        assertThatThrownBy { active.verifyEmail(createdAt.plusSeconds(120)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Only a pending user can verify an email")
        assertThatThrownBy { account.verifyEmail(createdAt.minusSeconds(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Email verification time must not precede the last update")
    }

    @Test
    fun `resets password for an active account`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val resetAt = Instant.parse("2026-09-18T14:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            null,
            PASSWORD_HASH,
            createdAt,
        ).verifyEmail(createdAt.plusSeconds(60))

        val newHash = PasswordHash.from("\$argon2id\$v=19\$m=65536,t=3,p=1\$new-fixture")
        val updated = account.resetPassword(newHash, resetAt)

        assertThat(updated.credential.hash).isEqualTo(newHash)
        assertThat(updated.credential.algorithm).isEqualTo(PasswordAlgorithm.ARGON2ID)
        assertThat(updated.credential.updatedAt).isEqualTo(resetAt)
        assertThat(updated.user).isEqualTo(account.user)
        assertThat(updated.roles).isEqualTo(account.roles)
    }

    @Test
    fun `does not reset password for a pending account`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            null,
            PASSWORD_HASH,
            createdAt,
        )

        val newHash = PasswordHash.from("\$argon2id\$v=19\$m=65536,t=3,p=1\$new-fixture")
        assertThatThrownBy { account.resetPassword(newHash, createdAt.plusSeconds(60)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Only an active user can reset a password")
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

    @Test
    fun `updates display name for an active account`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val updateAt = Instant.parse("2026-09-18T14:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Original",
            PASSWORD_HASH,
            createdAt,
        ).verifyEmail(createdAt.plusSeconds(60))

        val updated = account.updateDisplayName("Novo Nome", updateAt)

        assertThat(updated.user.displayName).isEqualTo("Novo Nome")
        assertThat(updated.user.updatedAt).isEqualTo(updateAt)
        assertThat(updated.user.email).isEqualTo(account.user.email)
        assertThat(updated.user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(updated.credential).isSameAs(account.credential)
        assertThat(updated.roles).isEqualTo(account.roles)
    }

    @Test
    fun `clears display name to null for an active account`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val updateAt = Instant.parse("2026-09-18T14:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Original",
            PASSWORD_HASH,
            createdAt,
        ).verifyEmail(createdAt.plusSeconds(60))

        val updated = account.updateDisplayName(null, updateAt)

        assertThat(updated.user.displayName).isNull()
        assertThat(updated.user.updatedAt).isEqualTo(updateAt)
    }

    @Test
    fun `rejects invalid display name or past update timestamp`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Original",
            PASSWORD_HASH,
            createdAt,
        ).verifyEmail(createdAt.plusSeconds(60))

        assertThatThrownBy { account.updateDisplayName("   ", createdAt.plusSeconds(120)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Display name length must be between 1 and 80 characters")

        assertThatThrownBy { account.updateDisplayName("a".repeat(81), createdAt.plusSeconds(120)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Display name length must be between 1 and 80 characters")

        assertThatThrownBy { account.updateDisplayName("Novo", createdAt) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Display name update time must not precede the last update")
    }

    @Test
    fun `does not update display name for a pending account`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val account = UserAccount.pending(
            USER_ID,
            Email.from("person@example.com"),
            "Original",
            PASSWORD_HASH,
            createdAt,
        )

        assertThatThrownBy { account.updateDisplayName("Novo", createdAt.plusSeconds(60)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Only an active user can update profile")
    }

    companion object {

        private val USER_ID = UserId.from(UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a901"))
        private val PASSWORD_HASH = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=3,p=1${'$'}fixture")
    }
}
