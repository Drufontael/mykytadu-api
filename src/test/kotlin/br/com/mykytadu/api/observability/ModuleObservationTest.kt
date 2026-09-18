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
