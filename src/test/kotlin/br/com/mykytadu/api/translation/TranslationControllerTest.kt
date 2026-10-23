package br.com.mykytadu.api.translation

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.security.ProblemAccessDeniedHandler
import br.com.mykytadu.api.security.ProblemAuthenticationEntryPoint
import br.com.mykytadu.api.security.SecurityConfiguration
import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslatedContent
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.api.TranslationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(TranslationController::class)
@AutoConfigureMockMvc
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    TranslationControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class TranslationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val service: StubTranslationService,
) {

    @BeforeEach
    fun reset() {
        service.outcome = TranslationOutcome.Success(
            TranslatedContent(
                translation = "Descrição traduzida",
                sourceLanguage = "en",
                targetLanguage = "pt-BR",
                contentType = "ANIME_DESCRIPTION",
                cached = false,
            ),
        )
    }

    @Test
    fun `translates valid request with 200 OK`() {
        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("user-123") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Anime description in English",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION",
                    "source": {
                        "system": "ANILIST",
                        "externalId": "15125"
                    }
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.translation") { value("Descrição traduzida") }
            jsonPath("$.sourceLanguage") { value("en") }
            jsonPath("$.targetLanguage") { value("pt-BR") }
            jsonPath("$.contentType") { value("ANIME_DESCRIPTION") }
            jsonPath("$.cached") { value(false) }
        }
    }

    @Test
    fun `returns 401 when request is not authenticated`() {
        mockMvc.post("/api/v1/translations") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Anime description",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("authentication_required") }
        }
    }

    @Test
    fun `returns 400 when text is blank or missing`() {
        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("user-123") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "   ",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.errors[0].field") { value("text") }
        }
    }

    @Test
    fun `returns 400 when language is invalid`() {
        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("user-123") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Valid text",
                    "sourceLanguage": "fr",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("request_validation_failed") }
            jsonPath("$.errors[0].field") { value("sourceLanguage") }
        }
    }

    @Test
    fun `returns 504 when provider times out`() {
        service.outcome = TranslationOutcome.Timeout

        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("user-123") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Valid text",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isGatewayTimeout() }
            jsonPath("$.code") { value("translation_provider_timeout") }
        }
    }

    @Test
    fun `returns 502 when provider fails`() {
        service.outcome = TranslationOutcome.ProviderFailed("Provider unavailable")

        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("user-123") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Valid text",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("translation_provider_failed") }
        }
    }

    @Test
    fun `returns 429 and Retry-After header when quota is exceeded`() {
        service.outcome = TranslationOutcome.QuotaExceeded(retryAfterSeconds = 3600)

        mockMvc.post("/api/v1/translations") {
            with(jwt().jwt { it.subject("018d45f3-524c-7000-8000-000000000099") })
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "text": "Valid text",
                    "sourceLanguage": "en",
                    "targetLanguage": "pt-BR",
                    "contentType": "ANIME_DESCRIPTION"
                }
            """.trimIndent()
        }.andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "3600") }
            jsonPath("$.code") { value("translation_quota_exceeded") }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun translationService(): StubTranslationService = StubTranslationService()

        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder { error("Mock JWT Decoder") }
    }

    class StubTranslationService : TranslationService {
        var outcome: TranslationOutcome = TranslationOutcome.Timeout

        override fun translate(command: TranslateContentCommand): TranslationOutcome = outcome
    }
}
