package br.com.mykytadu.identity.domain.model

data class RoleAssignment(val userId: UserId, val role: Role) {

    companion object {

        fun defaultFor(userId: UserId): RoleAssignment = RoleAssignment(userId, Role.USER)
    }
}
