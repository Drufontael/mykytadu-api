package br.com.mykytadu.identity.domain.model

import java.time.Instant

class User private constructor(
    val id: UserId,
    val email: Email,
    val displayName: String?,
    val status: UserStatus,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    init {
        require(!updatedAt.isBefore(createdAt)) { "User update time must not precede creation time" }
        require(status != UserStatus.PENDING || emailVerifiedAt == null) {
            "Pending user must not have a verified email timestamp"
        }
        require(status != UserStatus.ACTIVE || emailVerifiedAt != null) {
            "Active user must have a verified email timestamp"
        }
        require(emailVerifiedAt == null || !emailVerifiedAt.isBefore(createdAt)) {
            "Email verification time must not precede creation time"
        }
    }

    fun verifyEmail(verifiedAt: Instant): User {
        check(status == UserStatus.PENDING) { "Only a pending user can verify an email" }
        require(!verifiedAt.isBefore(updatedAt)) { "Email verification time must not precede the last update" }
        return User(
            id = id,
            email = email,
            displayName = displayName,
            status = UserStatus.ACTIVE,
            emailVerifiedAt = verifiedAt,
            createdAt = createdAt,
            updatedAt = verifiedAt,
        )
    }

    companion object {

        fun pending(id: UserId, email: Email, displayName: String?, now: Instant): User = User(
            id = id,
            email = email,
            displayName = normalizeDisplayName(displayName),
            status = UserStatus.PENDING,
            emailVerifiedAt = null,
            createdAt = now,
            updatedAt = now,
        )

        fun restore(
            id: UserId,
            email: Email,
            displayName: String?,
            status: UserStatus,
            emailVerifiedAt: Instant?,
            createdAt: Instant,
            updatedAt: Instant,
        ): User = User(
            id = id,
            email = email,
            displayName = normalizeDisplayName(displayName),
            status = status,
            emailVerifiedAt = emailVerifiedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        private fun normalizeDisplayName(displayName: String?): String? = displayName?.trim()?.also {
            require(it.length in DISPLAY_NAME_MIN_LENGTH..DISPLAY_NAME_MAX_LENGTH) {
                "Display name length must be between $DISPLAY_NAME_MIN_LENGTH and $DISPLAY_NAME_MAX_LENGTH characters"
            }
        }

        private const val DISPLAY_NAME_MIN_LENGTH = 1
        private const val DISPLAY_NAME_MAX_LENGTH = 80
    }
}
