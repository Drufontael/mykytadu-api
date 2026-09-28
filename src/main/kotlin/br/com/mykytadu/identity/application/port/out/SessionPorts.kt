package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import java.util.UUID

interface SessionStore {
    fun create(session: Session)
    fun update(session: Session)
    fun findById(sessionId: SessionId): Session?
    fun findByRefreshTokenHash(refreshTokenHash: TokenHash): Session?
    fun findFamilyForUpdate(tokenFamilyId: TokenFamilyId): List<Session>
    fun revokeRenewableFamily(tokenFamilyId: TokenFamilyId, revokedAt: java.time.Instant): Int
    fun revokeFamilyForLogout(tokenFamilyId: TokenFamilyId, revokedAt: java.time.Instant): Int
    fun revokeAllForUser(userId: UserId, revokedAt: java.time.Instant): Int
}

interface SessionIdGenerator {
    fun nextSessionId(): SessionId
    fun nextTokenFamilyId(): TokenFamilyId
    fun nextAccessTokenId(): UUID
}

interface SessionTokenCryptography {
    fun generateToken(): String
    fun hash(token: String): TokenHash
}

interface RefreshTokenDeriver {
    val activeKeyId: String

    fun derive(keyId: String, predecessorId: SessionId, successorId: SessionId, idempotencyKey: String): String?
}
