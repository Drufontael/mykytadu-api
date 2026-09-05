package br.com.mykytadu.api.observability

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ObservabilityFixtureController::class)
class ObservabilityHttpTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val meterRegistry: MeterRegistry,
) {
    @Test
    fun `uses the same generated trace id in header and problem body`() {
        val response = mockMvc.get("/test/observability/anonymous") {
            header(TraceIdFilter.TRACE_ID_HEADER, "untrusted-client-value")
        }.andReturn().response

        val traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER)
        assertThat(traceId).isNotBlank().isNotEqualTo("untrusted-client-value")
        assertThat(response.contentAsString).contains("\"traceId\":\"$traceId\"")
    }

    @Test
    @WithMockUser
    fun `records normalized route templates instead of concrete paths`() {
        mockMvc.get("/test/observability/items/6f09b5f9").andExpect { status { isOk() } }

        val timer = meterRegistry.find("http.server.requests")
            .tag("uri", "/test/observability/items/{itemId}")
            .timer()

        assertThat(timer).isNotNull()
        assertThat(timer?.count()).isOne()
        assertThat(
            meterRegistry.find("http.server.requests")
                .tag("uri", "/test/observability/items/6f09b5f9")
                .timer(),
        ).isNull()
    }
}

@RestController
private class ObservabilityFixtureController {
    @GetMapping("/test/observability/items/{itemId}")
    fun item(@PathVariable itemId: String) = mapOf("itemId" to itemId)
}
