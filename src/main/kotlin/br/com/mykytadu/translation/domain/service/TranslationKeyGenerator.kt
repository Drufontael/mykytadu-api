package br.com.mykytadu.translation.domain.service

import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object TranslationKeyGenerator {

    private const val DELIMITER = "\u0000"

    fun generateKey(
        normalizedText: String,
        sourceLanguage: Language,
        targetLanguage: Language,
        contentType: ContentType,
    ): String {
        val payload = buildString {
            append(normalizedText)
            append(DELIMITER)
            append(sourceLanguage.code)
            append(DELIMITER)
            append(targetLanguage.code)
            append(DELIMITER)
            append(contentType.value)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
