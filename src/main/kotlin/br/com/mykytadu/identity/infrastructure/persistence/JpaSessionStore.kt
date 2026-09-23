package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.domain.model.Session
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

internal interface SpringDataSessionRepository : JpaRepository<SessionEntity, UUID>

@Repository
@Profile("!test")
internal class JpaSessionStore(private val sessions: SpringDataSessionRepository) : SessionStore {

    @Transactional
    override fun create(session: Session) {
        sessions.saveAndFlush(
            SessionEntity(
                id = session.id.value,
                userId = session.userId.value,
                refreshTokenHash = session.refreshTokenHash.persistenceValue(),
                tokenFamilyId = session.tokenFamilyId.value,
                clientId = session.clientId,
                csrfTokenHash = session.csrfTokenHash?.persistenceValue(),
                expiresAt = session.expiresAt,
                revokedAt = session.revokedAt,
                revokeReason = session.revokeReason,
                createdAt = session.createdAt,
            ),
        )
    }
}
