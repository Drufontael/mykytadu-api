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
)
