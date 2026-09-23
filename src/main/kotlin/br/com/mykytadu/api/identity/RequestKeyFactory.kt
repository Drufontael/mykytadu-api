package br.com.mykytadu.api.identity

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
internal class RequestKeyFactory {

    fun from(request: HttpServletRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(request.remoteAddr.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
    }

    companion object {
        private const val BYTE_MASK = 0xFF
    }
}
