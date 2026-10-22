package br.com.mykytadu.identity.api

import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

@NamedInterface("api")
interface IdentityProfile {

    fun getProfile(query: GetProfileQuery): ProfileOutcome

    fun updateProfile(command: UpdateProfileCommand): ProfileOutcome
}

@NamedInterface("api")
data class GetProfileQuery(val userId: UUID) {
    override fun toString(): String = "GetProfileQuery(userId=[REDACTED])"
}

@NamedInterface("api")
data class UpdateProfileCommand(val userId: UUID, val displayName: String?) {
    override fun toString(): String = "UpdateProfileCommand(userId=[REDACTED], displayName=[REDACTED])"
}

@NamedInterface("api")
sealed interface ProfileOutcome {

    @NamedInterface("api")
    data class Success(val profile: UserProfileDto) : ProfileOutcome

    @NamedInterface("api")
    data object NotFound : ProfileOutcome

    @NamedInterface("api")
    data object Inactive : ProfileOutcome
}

@NamedInterface("api")
data class UserProfileDto(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val status: String,
    val roles: Set<String>,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    override fun toString(): String =
        "UserProfileDto(id=[REDACTED], email=[REDACTED], displayName=[REDACTED], status=$status, roles=$roles)"
}
