package br.com.mykytadu.identity.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sessions", schema = "identity")
internal class SessionEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "refresh_token_hash", nullable = false)
    val refreshTokenHash: String,
    @Column(name = "token_family_id", nullable = false)
    val tokenFamilyId: UUID,
    @Column(name = "client_id")
    val clientId: String?,
    @Column(name = "csrf_token_hash")
    val csrfTokenHash: String?,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "revoked_at")
    val revokedAt: Instant?,
    @Column(name = "revoke_reason")
    val revokeReason: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "parent_session_id")
    val parentSessionId: UUID?,
    @Column(name = "rotated_to_session_id")
    val rotatedToSessionId: UUID?,
    @Column(name = "rotation_idempotency_key_hash")
    val rotationIdempotencyKeyHash: String?,
    @Column(name = "refresh_derivation_kid")
    val refreshDerivationKeyId: String?,
    @Column(name = "replay_until")
    val replayUntil: Instant?,
    @Column(name = "replay_access_token_id")
    val replayAccessTokenId: UUID?,
    @Column(name = "replay_access_issued_at")
    val replayAccessIssuedAt: Instant?,
    @Column(name = "replay_access_expires_at")
    val replayAccessExpiresAt: Instant?,
)
