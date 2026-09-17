package br.com.mykytadu.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

@Component
class ApiProblemFactory {

    fun create(
        status: HttpStatus,
        code: ProblemCode,
        request: HttpServletRequest,
        traceId: String = currentTraceId(),
        errors: List<*> = emptyList<Any>(),
        detail: String = code.defaultDetail,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
        title = code.defaultTitle
        type = URI.create("urn:mykytadu:problem:${code.wireValue}")
        instance = URI.create(request.requestURI)
        setProperty("code", code.wireValue)
        setProperty("traceId", traceId)
        setProperty("errors", errors)
    }

    fun currentTraceId(): String = MDC.get("traceId") ?: UUID.randomUUID().toString()
}
