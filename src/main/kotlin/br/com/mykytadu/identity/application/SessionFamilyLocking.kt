package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Session

internal fun SessionStore.findSessionFamilyLocked(
    refreshToken: String,
    tokens: SessionTokenCryptography,
    accounts: UserAccountRepository,
): Session? = findByRefreshTokenHash(tokens.hash(refreshToken))?.let { findSessionFamilyLocked(it, accounts) }

internal fun SessionStore.findSessionFamilyLocked(candidate: Session, accounts: UserAccountRepository): Session? {
    if (accounts.findByIdForUpdate(candidate.userId) == null) return null
    return findFamilyForUpdate(candidate.tokenFamilyId).firstOrNull { it.id == candidate.id }
}
