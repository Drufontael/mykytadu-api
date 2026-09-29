package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.SessionRotationReplay
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

internal interface SpringDataSessionRepository : JpaRepository<SessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.tokenFamilyId = :familyId order by session.id")
    fun findFamilyForUpdate(@Param("familyId") familyId: UUID): List<SessionEntity>

    @Query("select session from SessionEntity session where session.refreshTokenHash = :refreshTokenHash")
    fun findByRefreshTokenHash(@Param("refreshTokenHash") refreshTokenHash: String): SessionEntity?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update SessionEntity session set session.revokedAt = :revokedAt, " +
            "session.revokeReason = 'reuse_detected' " +
            "where session.tokenFamilyId = :familyId and session.revokedAt is null " +
            "and session.expiresAt > :revokedAt",
    )
    fun revokeRenewableFamily(@Param("familyId") familyId: UUID, @Param("revokedAt") revokedAt: Instant): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update SessionEntity session set session.revokedAt = :revokedAt, " +
            "session.revokeReason = 'logout' " +
            "where session.tokenFamilyId = :familyId and session.revokedAt is null " +
            "and session.expiresAt > :revokedAt",
    )
    fun revokeFamilyForLogout(@Param("familyId") familyId: UUID, @Param("revokedAt") revokedAt: Instant): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update SessionEntity session set session.revokedAt = :revokedAt, " +
            "session.revokeReason = 'logout_all' " +
            "where session.userId = :userId and session.revokedAt is null " +
            "and session.expiresAt > :revokedAt",
    )
    fun revokeAllForUser(@Param("userId") userId: UUID, @Param("revokedAt") revokedAt: Instant): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update SessionEntity session set session.revokedAt = :revokedAt, " +
            "session.revokeReason = 'account_inactive' " +
            "where session.userId = :userId and session.revokedAt is null " +
            "and session.expiresAt > :revokedAt",
    )
    fun revokeAllForInactiveUser(@Param("userId") userId: UUID, @Param("revokedAt") revokedAt: Instant): Int
}

@Repository
@Profile("!test")
internal class JpaSessionStore(
    private val sessions: SpringDataSessionRepository,
    private val entityManager: EntityManager,
) : SessionStore {

    @Transactional
    override fun create(session: Session) {
        sessions.saveAndFlush(session.toEntity())
    }

    @Transactional
    override fun update(session: Session) {
        check(sessions.existsById(session.id.value)) { "Session to update does not exist" }
        sessions.saveAndFlush(session.toEntity())
    }

    @Transactional(readOnly = true)
    override fun findById(sessionId: SessionId): Session? = sessions.findById(sessionId.value).orElse(null)?.toDomain()

    @Transactional
    override fun findByRefreshTokenHash(refreshTokenHash: TokenHash): Session? =
        sessions.findByRefreshTokenHash(refreshTokenHash.persistenceValue())?.toDomain()

    @Transactional
    override fun findFamilyForUpdate(tokenFamilyId: TokenFamilyId): List<Session> =
        sessions.findFamilyForUpdate(tokenFamilyId.value)
            .onEach { entityManager.refresh(it, LockModeType.PESSIMISTIC_WRITE) }
            .map { it.toDomain() }

    @Transactional
    override fun revokeRenewableFamily(tokenFamilyId: TokenFamilyId, revokedAt: Instant): Int =
        sessions.revokeRenewableFamily(tokenFamilyId.value, revokedAt)

    @Transactional
    override fun revokeFamilyForLogout(tokenFamilyId: TokenFamilyId, revokedAt: Instant): Int =
        sessions.revokeFamilyForLogout(tokenFamilyId.value, revokedAt)

    @Transactional
    override fun revokeAllForUser(userId: UserId, revokedAt: Instant): Int =
        sessions.revokeAllForUser(userId.value, revokedAt)

    @Transactional
    override fun revokeAllForInactiveUser(userId: UserId, revokedAt: Instant): Int =
        sessions.revokeAllForInactiveUser(userId.value, revokedAt)

    private fun Session.toEntity(): SessionEntity = SessionEntity(
        id = id.value,
        userId = userId.value,
        refreshTokenHash = refreshTokenHash.persistenceValue(),
        tokenFamilyId = tokenFamilyId.value,
        clientId = clientId,
        csrfTokenHash = csrfTokenHash?.persistenceValue(),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        revokeReason = revokeReason,
        createdAt = createdAt,
        parentSessionId = parentSessionId?.value,
        rotatedToSessionId = rotationReplay?.successorSessionId?.value,
        rotationIdempotencyKeyHash = rotationReplay?.idempotencyKeyHash?.persistenceValue(),
        refreshDerivationKeyId = rotationReplay?.derivationKeyId,
        replayUntil = rotationReplay?.replayUntil,
        replayAccessTokenId = rotationReplay?.accessTokenId,
        replayAccessIssuedAt = rotationReplay?.accessIssuedAt,
        replayAccessExpiresAt = rotationReplay?.accessExpiresAt,
    )

    private fun SessionEntity.toDomain(): Session = Session.restore(
        id = SessionId.from(id),
        userId = UserId.from(userId),
        refreshTokenHash = TokenHash.sha256(refreshTokenHash),
        tokenFamilyId = TokenFamilyId.from(tokenFamilyId),
        clientId = clientId,
        csrfTokenHash = csrfTokenHash?.let(TokenHash::sha256),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        revokeReason = revokeReason,
        createdAt = createdAt,
        parentSessionId = parentSessionId?.let(SessionId::from),
        rotationReplay = rotatedToSessionId?.let { successorId ->
            SessionRotationReplay(
                successorSessionId = SessionId.from(successorId),
                idempotencyKeyHash = TokenHash.sha256(requireNotNull(rotationIdempotencyKeyHash)),
                derivationKeyId = requireNotNull(refreshDerivationKeyId),
                replayUntil = requireNotNull(replayUntil),
                accessTokenId = requireNotNull(replayAccessTokenId),
                accessIssuedAt = requireNotNull(replayAccessIssuedAt),
                accessExpiresAt = requireNotNull(replayAccessExpiresAt),
            )
        },
    )
}
