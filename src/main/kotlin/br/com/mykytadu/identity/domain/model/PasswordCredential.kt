package br.com.mykytadu.identity.domain.model

import java.time.Instant

class PasswordCredential private constructor(
    val userId: UserId,
    val hash: PasswordHash,
    val algorithm: PasswordAlgorithm,
    val updatedAt: Instant,
) {

    companion object {

        fun argon2id(userId: UserId, hash: PasswordHash, now: Instant): PasswordCredential = PasswordCredential(
            userId = userId,
            hash = hash,
            algorithm = PasswordAlgorithm.ARGON2ID,
            updatedAt = now,
        )

        fun restore(
            userId: UserId,
            hash: PasswordHash,
            algorithm: PasswordAlgorithm,
            updatedAt: Instant,
        ): PasswordCredential = PasswordCredential(userId, hash, algorithm, updatedAt)
    }
}
