package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordAlgorithm
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.Role
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus

internal object UserAccountPersistenceMapper {

    fun toEntity(account: UserAccount): UserAccountEntity = UserAccountEntity(
        id = account.user.id.value,
        profile = UserProfileEmbeddable(
            email = account.user.email.address,
            normalizedEmail = account.user.email.normalized,
            displayName = account.user.displayName,
            status = account.user.status.persistenceValue,
            emailVerifiedAt = account.user.emailVerifiedAt,
            createdAt = account.user.createdAt,
            updatedAt = account.user.updatedAt,
        ),
        credential = PasswordCredentialEmbeddable(
            passwordHash = account.credential.hash.encodedValue(),
            algorithm = account.credential.algorithm.persistenceValue,
            updatedAt = account.credential.updatedAt,
        ),
        roles = account.roles.map { it.role.name }.toSet(),
    )

    fun toDomain(entity: UserAccountEntity): UserAccount {
        val userId = UserId.from(entity.id)
        val user = User.restore(
            id = userId,
            email = Email.from(entity.profile.email),
            displayName = entity.profile.displayName,
            status = UserStatus.fromPersistenceValue(entity.profile.status),
            emailVerifiedAt = entity.profile.emailVerifiedAt,
            createdAt = entity.profile.createdAt,
            updatedAt = entity.profile.updatedAt,
        )
        val credential = PasswordCredential.restore(
            userId = userId,
            hash = PasswordHash.from(entity.credential.passwordHash),
            algorithm = PasswordAlgorithm.fromPersistenceValue(entity.credential.algorithm),
            updatedAt = entity.credential.updatedAt,
        )
        val roles = entity.roles.mapTo(mutableSetOf()) {
            RoleAssignment(userId, Role.fromPersistenceValue(it))
        }
        return UserAccount.restore(user, credential, roles)
    }
}
