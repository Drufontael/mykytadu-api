package br.com.mykytadu.api.security

import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.error.ProblemCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ProblemAccessDeniedHandler(
    private val problemFactory: ApiProblemFactory,
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val problem = problemFactory.create(
            status = HttpStatus.FORBIDDEN,
            code = ProblemCode.AUTHORIZATION_DENIED,
            request = request,
        )

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        objectMapper.writeValue(response.outputStream, problem)
    }
}
