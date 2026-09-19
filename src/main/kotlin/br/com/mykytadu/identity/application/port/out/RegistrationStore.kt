package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserAccount
import java.time.Instant

interface RegistrationStore {

    @Throws(RegistrationConflictException::class)
    fun create(account: UserAccount, verificationToken: ActionToken)

    fun replaceVerificationToken(email: Email, token: VerificationTokenDraft, invalidatedAt: Instant): String?
}

class RegistrationConflictException(cause: Throwable) :
    RuntimeException("Registration conflicts with existing data", cause)

data class VerificationTokenDraft(
    val id: ActionTokenId,
    val tokenHash: TokenHash,
    val createdAt: Instant,
    val expiresAt: Instant,
)
