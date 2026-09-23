package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.ActionTokenId
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.TokenHash
import br.com.mykytadu.identity.domain.model.UserId

interface IdentityIdGenerator {

    fun nextUserId(): UserId

    fun nextActionTokenId(): ActionTokenId
}

interface PasswordHasher {

    fun hash(password: CharSequence): PasswordHash
}

interface PasswordVerifier {

    fun matches(password: CharSequence, expectedHash: PasswordHash?): Boolean
}

interface ActionTokenCryptography {

    fun generateToken(): String

    fun hash(token: String): TokenHash
}
