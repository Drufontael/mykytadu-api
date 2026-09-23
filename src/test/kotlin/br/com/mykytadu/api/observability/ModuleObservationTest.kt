package br.com.mykytadu.api.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ModuleObservationFixtureController::class)
class ModuleObservationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val meterRegistry: MeterRegistry,
) {

    @Test
    @WithMockUser
    fun `separates domain and infrastructure failures with bounded tags`() {
        val domainBefore = counter(
            ObservedModule.IDENTITY,
            ObservationEvent.OPERATION_REJECTED,
            ObservationCategory.DOMAIN,
            ObservationCode.REGISTRATION_REJECTED,
            ObservationOutcome.FAILURE,
        )
        val infrastructureBefore = counter(
            ObservedModule.TRANSLATION,
            ObservationEvent.DEPENDENCY_FAILED,
            ObservationCategory.INFRASTRUCTURE,
            ObservationCode.DATABASE_UNAVAILABLE,
            ObservationOutcome.FAILURE,
        )

        mockMvc.get("/test/observability/modules/identity/domain")
            .andExpect { status { isOk() } }
        mockMvc.get("/test/observability/modules/translation/infrastructure")
            .andExpect { status { isOk() } }

        assertThat(
            counter(
                ObservedModule.IDENTITY,
                ObservationEvent.OPERATION_REJECTED,
                ObservationCategory.DOMAIN,
                ObservationCode.REGISTRATION_REJECTED,
                ObservationOutcome.FAILURE,
            ),
        ).isEqualTo(domainBefore + 1.0)
        assertThat(
            counter(
                ObservedModule.TRANSLATION,
                ObservationEvent.DEPENDENCY_FAILED,
                ObservationCategory.INFRASTRUCTURE,
                ObservationCode.DATABASE_UNAVAILABLE,
                ObservationOutcome.FAILURE,
            ),
        ).isEqualTo(infrastructureBefore + 1.0)

        val tags = meterRegistry.find(ModuleObservation.METRIC_NAME)
            .tag("module", "identity")
            .tag("event", "operation_rejected")
            .tag("category", "domain")
            .tag("code", "registration_rejected")
            .tag("outcome", "failure")
            .counter()!!.id.tags.map { it.key }
        assertThat(tags).containsExactlyInAnyOrder("module", "event", "category", "code", "outcome")
    }

    @Test
    @WithMockUser
    fun `keeps the request trace in the event context without metric cardinality`() {
        val logger = LoggerFactory.getLogger(ModuleObservation::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val response = mockMvc.get("/test/observability/modules/identity/domain")
                .andExpect { status { isOk() } }
                .andReturn().response

            val event = appender.list.single { it.formattedMessage == "module_observation" }
            assertThat(event.mdcPropertyMap[TraceIdFilter.TRACE_ID])
                .isEqualTo(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
            assertThat(event.mdcPropertyMap.keys).containsExactly(TraceIdFilter.TRACE_ID)
            assertThat(event.formattedMessage)
                .doesNotContain("@", "Bearer", "token", "password", "authorization")
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun counter(
        module: ObservedModule,
        event: ObservationEvent,
        category: ObservationCategory,
        code: ObservationCode,
        outcome: ObservationOutcome,
    ): Double = meterRegistry.find(ModuleObservation.METRIC_NAME)
        .tag("module", module.wireValue)
        .tag("event", event.wireValue)
        .tag("category", category.wireValue)
        .tag("code", code.wireValue)
        .tag("outcome", outcome.wireValue)
        .counter()?.count() ?: 0.0
}

@RestController
private class ModuleObservationFixtureController(private val moduleObservation: ModuleObservation) {
    @GetMapping("/test/observability/modules/identity/domain")
    fun recordIdentityDomainFailure() {
        moduleObservation.record(
            ModuleObservationEvent(
                module = ObservedModule.IDENTITY,
                event = ObservationEvent.OPERATION_REJECTED,
                category = ObservationCategory.DOMAIN,
                code = ObservationCode.REGISTRATION_REJECTED,
                outcome = ObservationOutcome.FAILURE,
            ),
        )
    }

    @GetMapping("/test/observability/modules/translation/infrastructure")
    fun recordTranslationInfrastructureFailure() {
        moduleObservation.record(
            ModuleObservationEvent(
                module = ObservedModule.TRANSLATION,
                event = ObservationEvent.DEPENDENCY_FAILED,
                category = ObservationCategory.INFRASTRUCTURE,
                code = ObservationCode.DATABASE_UNAVAILABLE,
                outcome = ObservationOutcome.FAILURE,
            ),
        )
    }
}
