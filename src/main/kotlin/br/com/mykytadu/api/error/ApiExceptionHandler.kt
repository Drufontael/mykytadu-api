package br.com.mykytadu.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val errors = exception.bindingResult.fieldErrors
            .map { FieldViolation(field = it.field, message = it.defaultMessage ?: "Invalid value") }
            .sortedBy { it.field }

        return problem(
            status = HttpStatus.BAD_REQUEST,
            code = "request_validation_failed",
            title = "Request validation failed",
            detail = "One or more fields are invalid.",
            request = request,
            errors = errors,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ProblemDetail {
        val traceId = currentTraceId()
        logger.error("Unexpected request failure traceId={}", traceId, exception)

        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "internal_error",
            title = "Internal server error",
            detail = "An unexpected error occurred.",
            request = request,
            traceId = traceId,
        )
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        title: String,
        detail: String,
        request: HttpServletRequest,
        traceId: String = currentTraceId(),
        errors: List<FieldViolation> = emptyList(),
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
        this.title = title
        type = URI.create("urn:mykytadu:problem:$code")
        instance = URI.create(request.requestURI)
        setProperty("code", code)
        setProperty("traceId", traceId)
        setProperty("errors", errors)
    }

    private fun currentTraceId(): String = MDC.get("traceId") ?: UUID.randomUUID().toString()

    private data class FieldViolation(val field: String, val code: String = "invalid", val message: String)

    companion object {
        private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
