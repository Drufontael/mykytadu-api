package br.com.mykytadu.identity.application.port.out

import br.com.mykytadu.identity.domain.model.TokenHash
import java.time.Instant

interface EmailVerificationStore {

    fun verify(tokenHash: TokenHash, verifiedAt: Instant): Boolean
}
