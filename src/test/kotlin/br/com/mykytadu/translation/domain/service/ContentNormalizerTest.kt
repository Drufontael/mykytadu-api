package br.com.mykytadu.translation.domain.service

import br.com.mykytadu.translation.domain.exception.InvalidContentException
import br.com.mykytadu.translation.domain.model.TranslationFormat
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ContentNormalizerTest {

    @Test
    fun `normalizes line breaks and whitespace for plain text`() {
        val raw = "  Line 1\r\nLine 2\rLine 3  "

        val result = ContentNormalizer.normalize(raw)

        assertThat(result.normalizedText.value).isEqualTo("Line 1\nLine 2\nLine 3")
        assertThat(result.format).isEqualTo(TranslationFormat.TEXT)
    }

    @Test
    fun `normalizes unicode to NFC form`() {
        // NFD form: e + combining acute accent
        val nfd = "e\u0301cole"
        val expectedNfc = "école"

        val result = ContentNormalizer.normalize(nfd)

        assertThat(result.normalizedText.value).isEqualTo(expectedNfc)
    }

    @Test
    fun `preserves japanese characters`() {
        val japaneseText = "進撃の巨人 - Attack on Titan"

        val result = ContentNormalizer.normalize(japaneseText)

        assertThat(result.normalizedText.value).isEqualTo(japaneseText)
        assertThat(result.format).isEqualTo(TranslationFormat.TEXT)
    }

    @Test
    fun `accepts safe HTML tags and sets format to HTML`() {
        val raw = "<p>First paragraph.</p><br /><br><i>Italic Note:</i> <b>bold text</b> and <em>emphasis</em>"

        val result = ContentNormalizer.normalize(raw)

        assertThat(result.normalizedText.value).isEqualTo(raw)
        assertThat(result.format).isEqualTo(TranslationFormat.HTML)
    }

    @Test
    fun `rejects blank or empty content`() {
        assertThatThrownBy { ContentNormalizer.normalize("   \r\n\t  ") }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("empty or blank")
    }

    @Test
    fun `rejects content exceeding max length`() {
        val longText = "a".repeat(10_001)

        assertThatThrownBy { ContentNormalizer.normalize(longText) }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("exceeds maximum allowed length")
    }

    @Test
    fun `rejects script tag`() {
        val malicious = "Description text <script>alert('xss')</script>"

        assertThatThrownBy { ContentNormalizer.normalize(malicious) }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("unsafe or forbidden HTML")
    }

    @Test
    fun `rejects iframe tag`() {
        val malicious = "Description text <iframe src='http://evil.com'></iframe>"

        assertThatThrownBy { ContentNormalizer.normalize(malicious) }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("unsafe or forbidden HTML")
    }

    @Test
    fun `rejects event handler attributes`() {
        val malicious = "<b onclick='steal()'>click me</b>"

        assertThatThrownBy { ContentNormalizer.normalize(malicious) }
            .isInstanceOf(InvalidContentException::class.java)
    }

    @Test
    fun `rejects disallowed HTML tags like img and div`() {
        val raw = "<div><img src='test.jpg' />Hello</div>"

        assertThatThrownBy { ContentNormalizer.normalize(raw) }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("not permitted in anime descriptions")
    }

    @Test
    fun `rejects allowed HTML tags with attributes`() {
        val raw = "<p style='color:red;'>Styled paragraph</p>"

        assertThatThrownBy { ContentNormalizer.normalize(raw) }
            .isInstanceOf(InvalidContentException::class.java)
            .hasMessageContaining("must not contain attributes")
    }
}
