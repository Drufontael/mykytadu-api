package br.com.mykytadu.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler(private val problemFactory: ApiProblemFactory) {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val errors = exception.bindingResult.fieldErrors
            .map { FieldViolation(field = it.field, message = it.defaultMessage ?: "Invalid value") }
            .sortedBy { it.field }

        return problemFactory.create(
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
        val traceId = problemFactory.currentTraceId()
        logger.error("Unexpected request failure traceId={}", traceId, exception)

        return problemFactory.create(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "internal_error",
            title = "Internal server error",
            detail = "An unexpected error occurred.",
            request = request,
            traceId = traceId,
        )
    }

    private data class FieldViolation(val field: String, val code: String = "invalid", val message: String)

    companion object {
        private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
