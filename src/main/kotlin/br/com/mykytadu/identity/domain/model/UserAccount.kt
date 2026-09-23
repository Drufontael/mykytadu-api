package br.com.mykytadu.identity.domain.model

import java.time.Instant

class UserAccount private constructor(val user: User, val credential: PasswordCredential, roles: Set<RoleAssignment>) {

    val roles: Set<RoleAssignment> = roles.toSet()

    init {
        require(credential.userId == user.id) { "Credential must belong to the user" }
        require(this.roles.isNotEmpty()) { "User must have at least one role" }
        require(this.roles.all { it.userId == user.id }) { "Every role must belong to the user" }
        require(this.roles.any { it.role == Role.USER }) { "User must have the default USER role" }
    }

    fun verifyEmail(verifiedAt: Instant): UserAccount = UserAccount(
        user = user.verifyEmail(verifiedAt),
        credential = credential,
        roles = roles,
    )

    companion object {

        fun pending(
            id: UserId,
            email: Email,
            displayName: String?,
            passwordHash: PasswordHash,
            now: Instant,
        ): UserAccount = UserAccount(
            user = User.pending(id, email, displayName, now),
            credential = PasswordCredential.argon2id(id, passwordHash, now),
            roles = setOf(RoleAssignment.defaultFor(id)),
        )

        fun restore(user: User, credential: PasswordCredential, roles: Set<RoleAssignment>): UserAccount =
            UserAccount(user, credential, roles)
    }
}
