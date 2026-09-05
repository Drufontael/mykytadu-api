package br.com.mykytadu.api.security

import br.com.mykytadu.api.error.ApiProblemFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ProblemAuthenticationEntryPoint(
    private val problemFactory: ApiProblemFactory,
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val problem = problemFactory.create(
            status = HttpStatus.UNAUTHORIZED,
            code = "authentication_required",
            title = "Authentication required",
            detail = "Valid authentication credentials are required.",
            request = request,
        )

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        objectMapper.writeValue(response.outputStream, problem)
    }
}
