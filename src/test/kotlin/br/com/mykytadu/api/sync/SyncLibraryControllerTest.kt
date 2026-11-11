package br.com.mykytadu.api.sync

import br.com.mykytadu.api.error.ApiExceptionHandler
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.security.ProblemAccessDeniedHandler
import br.com.mykytadu.api.security.ProblemAuthenticationEntryPoint
import br.com.mykytadu.api.security.SecurityConfiguration
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
import java.util.UUID

@WebMvcTest(SyncLibraryController::class)
@AutoConfigureMockMvc
@Import(
    ApiExceptionHandler::class,
    ApiProblemFactory::class,
    SecurityConfiguration::class,
    ProblemAuthenticationEntryPoint::class,
    ProblemAccessDeniedHandler::class,
    SyncLibraryControllerTest.TestBeans::class,
)
@ActiveProfiles("web-test")
class SyncLibraryControllerTest(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `sync without authentication returns 401`() {
        mockMvc.post("/api/v1/sync/library") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `sync with valid bearer token returns 200 with synced batch response`() {
        val userId = UUID.randomUUID().toString()
        val body = """
            {
              "lastSyncedAtEpochMillis": 1000,
              "mutations": [
                {
                  "animeId": "anime-123",
                  "type": "UPSERT",
                  "payload": {
                    "animeId": "anime-123",
                    "status": "WATCHING",
                    "progressCurrent": 3,
                    "progressTotal": 12,
                    "isFavorite": true,
                    "rating": 8,
                    "notes": "Good anime",
                    "createdAtEpochMillis": 1000,
                    "updatedAtEpochMillis": 2000
                  },
                  "timestampEpochMillis": 2000
                }
              ]
            }
        """.trimIndent()

        mockMvc.post("/api/v1/sync/library") {
            with(jwt().jwt { it.subject(userId) })
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.appliedCount") { value(1) }
            jsonPath("$.syncedAtEpochMillis") { isNumber() }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder { error("Mock JWT") }
    }
}
