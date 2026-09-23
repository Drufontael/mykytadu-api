package br.com.mykytadu.identity.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SessionTest {

    @Test
    fun `creates native and Web sessions with only hashed credentials`() {
        val native = session(clientId = "mykytadu-android", csrfHash = null)
        val web = session(clientId = Session.WEB_CLIENT_ID, csrfHash = CSRF_HASH)

        assertThat(native.refreshTokenHash.toString()).isEqualTo("[REDACTED]")
        assertThat(native.csrfTokenHash).isNull()
        assertThat(web.csrfTokenHash).isEqualTo(CSRF_HASH)
    }

    @Test
    fun `rejects csrf outside Web and missing csrf for Web`() {
        assertThatThrownBy { session(clientId = Session.WEB_CLIENT_ID, csrfHash = null) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { session(clientId = "mykytadu-ios", csrfHash = CSRF_HASH) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun session(clientId: String?, csrfHash: TokenHash?): Session = Session.initial(
        SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010")),
        UserId.from(UUID.fromString("019937b6-3600-7001-8000-000000000001")),
        REFRESH_HASH,
        TokenFamilyId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011")),
        clientId,
        csrfHash,
        NOW,
        NOW.plusSeconds(60),
    )

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val REFRESH_HASH = TokenHash.sha256("a".repeat(64))
        private val CSRF_HASH = TokenHash.sha256("b".repeat(64))
    }
}
