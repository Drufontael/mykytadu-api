package br.com.mykytadu.identity.infrastructure.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.SecondaryTable
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "identity")
@SecondaryTable(
    name = "password_credentials",
    schema = "identity",
    pkJoinColumns = [jakarta.persistence.PrimaryKeyJoinColumn(name = "user_id")],
)
internal class UserAccountEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,
    @Embedded
    var profile: UserProfileEmbeddable,
    @Embedded
    val credential: PasswordCredentialEmbeddable,
    roles: Set<String>,
) {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "roles",
        schema = "identity",
        joinColumns = [JoinColumn(name = "user_id")],
    )
    @Column(name = "role", nullable = false)
    val roles: MutableSet<String> = roles.toMutableSet()
}

@Embeddable
internal class UserProfileEmbeddable(
    @Column(name = "email", nullable = false)
    val email: String,
    @Column(name = "normalized_email", nullable = false)
    val normalizedEmail: String,
    @Column(name = "display_name")
    val displayName: String?,
    @Column(name = "status", nullable = false)
    val status: String,
    @Column(name = "email_verified_at")
    val emailVerifiedAt: Instant?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)

@Embeddable
internal class PasswordCredentialEmbeddable(
    @Column(name = "password_hash", table = "password_credentials", nullable = false)
    val passwordHash: String,
    @Column(name = "algorithm", table = "password_credentials", nullable = false)
    val algorithm: String,
    @Column(name = "updated_at", table = "password_credentials", nullable = false)
    val updatedAt: Instant,
)
