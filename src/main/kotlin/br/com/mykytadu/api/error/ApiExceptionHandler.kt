package br.com.mykytadu.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler(private val problemFactory: ApiProblemFactory) {

    @ExceptionHandler(ApiProblemException::class)
    fun handleApiProblem(exception: ApiProblemException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val problem = problemFactory.create(
            status = exception.status,
            code = exception.code,
            request = request,
        )
        return ResponseEntity.status(exception.status)
            .headers { headers ->
                exception.retryAfterSeconds?.let { headers.set(HttpHeaders.RETRY_AFTER, it.toString()) }
            }
            .body(problem)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val errors = exception.bindingResult.fieldErrors
            .map { FieldViolation(field = it.field, message = it.defaultMessage ?: "Invalid value") }
            .sortedBy { it.field }

        return problemFactory.create(
            status = HttpStatus.BAD_REQUEST,
            code = ProblemCode.REQUEST_VALIDATION_FAILED,
            request = request,
            errors = errors,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(request: HttpServletRequest): ProblemDetail = problemFactory.create(
        status = HttpStatus.BAD_REQUEST,
        code = ProblemCode.REQUEST_VALIDATION_FAILED,
        request = request,
        detail = "The request body is missing or malformed.",
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ProblemDetail {
        val traceId = problemFactory.currentTraceId()
        logger.error("Unexpected request failure traceId={}", traceId, exception)

        return problemFactory.create(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = ProblemCode.INTERNAL_ERROR,
            request = request,
            traceId = traceId,
        )
    }

    private data class FieldViolation(val field: String, val code: String = "invalid", val message: String)

    companion object {
        private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
