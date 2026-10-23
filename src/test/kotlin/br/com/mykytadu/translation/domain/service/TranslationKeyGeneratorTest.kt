package br.com.mykytadu.translation.domain.service

import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TranslationKeyGeneratorTest {

    @Test
    fun `generates 64 character hex SHA-256 hash deterministically`() {
        val key1 = TranslationKeyGenerator.generateKey(
            normalizedText = "A brilliant anime description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val key2 = TranslationKeyGenerator.generateKey(
            normalizedText = "A brilliant anime description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThat(key1).hasSize(64)
        assertThat(key1).matches("^[0-9a-f]{64}$")
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `generates distinct keys for different parameters`() {
        val base = TranslationKeyGenerator.generateKey(
            normalizedText = "Description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val differentText = TranslationKeyGenerator.generateKey(
            normalizedText = "Different description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val differentSource = TranslationKeyGenerator.generateKey(
            normalizedText = "Description",
            sourceLanguage = Language.JA,
            targetLanguage = Language.PT_BR,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        val differentTarget = TranslationKeyGenerator.generateKey(
            normalizedText = "Description",
            sourceLanguage = Language.EN,
            targetLanguage = Language.AUTO,
            contentType = ContentType.ANIME_DESCRIPTION,
        )

        assertThat(base).isNotEqualTo(differentText)
        assertThat(base).isNotEqualTo(differentSource)
        assertThat(base).isNotEqualTo(differentTarget)
    }
}
