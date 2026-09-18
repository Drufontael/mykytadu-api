package br.com.mykytadu.api.observability

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component

enum class ObservedModule(val wireValue: String) {
    IDENTITY("identity"),
    TRANSLATION("translation"),
}

enum class ObservationEvent(val wireValue: String) {
    OPERATION_ACCEPTED("operation_accepted"),
    OPERATION_REJECTED("operation_rejected"),
    DEPENDENCY_FAILED("dependency_failed"),
}

enum class ObservationCategory(val wireValue: String) {
    DOMAIN("domain"),
    INFRASTRUCTURE("infrastructure"),
}

enum class ObservationOutcome(val wireValue: String) {
    SUCCESS("success"),
    FAILURE("failure"),
}

enum class ObservationCode(val wireValue: String) {
    OPERATION_SUCCEEDED("operation_succeeded"),
    REGISTRATION_REJECTED("registration_rejected"),
    AUTHORIZATION_DENIED("authorization_denied"),
    DATABASE_UNAVAILABLE("database_unavailable"),
    DATABASE_CONSTRAINT_VIOLATION("database_constraint_violation"),
    TRANSLATION_PROVIDER_FAILED("translation_provider_failed"),
    TRANSLATION_PROVIDER_TIMEOUT("translation_provider_timeout"),
}

data class ModuleObservationEvent(
    val module: ObservedModule,
    val event: ObservationEvent,
    val category: ObservationCategory,
    val code: ObservationCode,
    val outcome: ObservationOutcome,
)

@Component
class ModuleObservation(private val meterRegistry: MeterRegistry) {
    fun record(observation: ModuleObservationEvent) {
        meterRegistry.counter(
            METRIC_NAME,
            "module",
            observation.module.wireValue,
            "event",
            observation.event.wireValue,
            "category",
            observation.category.wireValue,
            "code",
            observation.code.wireValue,
            "outcome",
            observation.outcome.wireValue,
        ).increment()

        val log = if (observation.outcome == ObservationOutcome.FAILURE) {
            logger.atWarn()
        } else {
            logger.atInfo()
        }
        val traceId = MDC.get(TraceIdFilter.TRACE_ID)
        if (traceId == null) {
            log.addKeyValue("traceId", "not_available")
        }
        log.addKeyValue("module", observation.module.wireValue)
            .addKeyValue("event", observation.event.wireValue)
            .addKeyValue("category", observation.category.wireValue)
            .addKeyValue("code", observation.code.wireValue)
            .addKeyValue("outcome", observation.outcome.wireValue)
            .log("module_observation")
    }

    companion object {
        const val METRIC_NAME = "mykytadu.module.events"
        private val logger = LoggerFactory.getLogger(ModuleObservation::class.java)
    }
}
