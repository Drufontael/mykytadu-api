package br.com.mykytadu.identity.infrastructure.persistence

import br.com.mykytadu.identity.domain.model.ActionToken
import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.ActionTokenType
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "action_tokens", schema = "identity")
internal class ActionTokenEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "type", nullable = false)
    val type: String,
    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "consumed_at")
    var consumedAt: Instant?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

internal fun ActionTokenEntity.toDomain(): ActionToken = ActionToken.restore(
    id = ActionTokenId.from(id),
    userId = UserId.from(userId),
    type = ActionTokenType.fromPersistenceValue(type),
    tokenHash = TokenHash.sha256(tokenHash),
    expiresAt = expiresAt,
    consumedAt = consumedAt,
    createdAt = createdAt,
)
