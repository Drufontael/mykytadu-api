package br.com.mykytadu.translation.domain.service

import br.com.mykytadu.translation.domain.exception.InvalidContentException
import br.com.mykytadu.translation.domain.model.NormalizedText
import br.com.mykytadu.translation.domain.model.TranslationFormat
import java.text.Normalizer
import java.util.Locale

data class NormalizedContent(val normalizedText: NormalizedText, val format: TranslationFormat)

object ContentNormalizer {

    private val ALLOWED_TAGS = setOf("br", "p", "b", "i", "em", "strong")

    private val HTML_TAG_REGEX = Regex("""<(/?)(\w+)([^>]*)>""")

    private val DANGEROUS_PATTERNS = listOf(
        Regex("""(?i)<script.*?>.*?</script.*?>"""),
        Regex("""(?i)<iframe.*?>"""),
        Regex("""(?i)on\w+\s*="""),
        Regex("""(?i)javascript\s*:"""),
    )

    fun normalize(rawText: String): NormalizedContent {
        validateSafety(rawText)

        val unicodeNormalized = Normalizer.normalize(rawText, Normalizer.Form.NFC)
        val lineBreaksNormalized = unicodeNormalized.replace("\r\n", "\n").replace("\r", "\n")
        val trimmed = lineBreaksNormalized.trim()

        validateLength(trimmed)
        val hasHtmlTags = validateHtmlTags(trimmed)
        val format = if (hasHtmlTags) TranslationFormat.HTML else TranslationFormat.TEXT

        return NormalizedContent(
            normalizedText = NormalizedText.of(trimmed),
            format = format,
        )
    }

    private fun validateSafety(rawText: String) {
        if (rawText.isBlank()) {
            throw InvalidContentException("Text must not be empty or blank")
        }
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(rawText)) {
                throw InvalidContentException("Content contains unsafe or forbidden HTML elements")
            }
        }
    }

    private fun validateLength(trimmed: String) {
        if (trimmed.isEmpty()) {
            throw InvalidContentException("Text must not be empty or blank after normalization")
        }
        if (trimmed.length > NormalizedText.MAX_LENGTH) {
            throw InvalidContentException(
                "Text length (${trimmed.length}) exceeds maximum allowed length of ${NormalizedText.MAX_LENGTH}",
            )
        }
    }

    private fun validateHtmlTags(trimmed: String): Boolean {
        var hasHtmlTags = false
        val matches = HTML_TAG_REGEX.findAll(trimmed)
        for (match in matches) {
            val tagName = match.groupValues[2].lowercase(Locale.ROOT)
            val attributes = match.groupValues[3].trim().removeSuffix("/").trim()

            if (!ALLOWED_TAGS.contains(tagName)) {
                throw InvalidContentException("HTML tag '<$tagName>' is not permitted in anime descriptions")
            }
            if (attributes.isNotEmpty()) {
                throw InvalidContentException("HTML tag '<$tagName>' must not contain attributes")
            }
            hasHtmlTags = true
        }
        return hasHtmlTags
    }
}
