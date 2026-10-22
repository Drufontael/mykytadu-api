package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.TokenHash
import java.time.Instant

interface PasswordRecoveryStore {

    fun createPasswordResetToken(email: Email, token: PasswordResetTokenDraft, invalidatedAt: Instant): String?

    fun resetPassword(tokenHash: TokenHash, passwordHash: PasswordHash, now: Instant): Boolean
}

data class PasswordResetTokenDraft(
    val id: ActionTokenId,
    val tokenHash: TokenHash,
    val createdAt: Instant,
    val expiresAt: Instant,
)
