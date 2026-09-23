package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.TokenFamilyId
import br.com.mykytadu.identity.domain.model.TokenHash
import java.util.UUID

interface SessionStore {
    fun create(session: Session)
}

interface SessionIdGenerator {
    fun nextSessionId(): SessionId
    fun nextTokenFamilyId(): TokenFamilyId
    fun nextAccessTokenId(): UUID
}

interface SessionTokenCryptography {
    fun generateToken(): String
    fun hash(token: String): TokenHash
}
