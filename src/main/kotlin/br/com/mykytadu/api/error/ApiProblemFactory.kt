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
        code: String,
        title: String,
        detail: String,
        request: HttpServletRequest,
        traceId: String = currentTraceId(),
        errors: List<*> = emptyList<Any>(),
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
        this.title = title
        type = URI.create("urn:mykytadu:problem:$code")
        instance = URI.create(request.requestURI)
        setProperty("code", code)
        setProperty("traceId", traceId)
        setProperty("errors", errors)
    }

    fun currentTraceId(): String = MDC.get("traceId") ?: UUID.randomUUID().toString()
}
