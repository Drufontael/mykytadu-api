package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId

interface UserAccountRepository {

    fun save(account: UserAccount): UserAccount

    fun findByEmail(email: Email): UserAccount?

    fun findById(userId: UserId): UserAccount?

    fun findByIdForUpdate(userId: UserId): UserAccount?
}
