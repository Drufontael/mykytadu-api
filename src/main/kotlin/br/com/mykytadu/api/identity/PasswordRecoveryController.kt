package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.identity.api.IdentityPasswordRecovery
import br.com.mykytadu.identity.api.RequestPasswordResetCommand
import br.com.mykytadu.identity.api.RequestPasswordResetOutcome
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("!test")
@RequestMapping("/api/v1/auth/password")
internal class PasswordRecoveryController(
    private val recovery: IdentityPasswordRecovery,
    private val requestKeyFactory: RequestKeyFactory,
) {

    @PostMapping("/forgot")
    fun forgotPassword(
        @Valid @RequestBody request: PasswordForgotRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ActionAcceptedResponse> {
        val outcome = recovery.requestPasswordReset(
            RequestPasswordResetCommand(
                email = request.email,
                requestKey = requestKeyFactory.from(servletRequest),
            ),
        )
        return when (outcome) {
            RequestPasswordResetOutcome.Accepted -> ResponseEntity.accepted().body(ActionAcceptedResponse(true))

            is RequestPasswordResetOutcome.RateLimited -> throw ApiProblemException(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemCode.RATE_LIMIT_EXCEEDED,
                outcome.retryAfterSeconds,
            )
        }
    }
}

internal data class PasswordForgotRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,
) {
    override fun toString(): String = "PasswordForgotRequest(email=[REDACTED])"
}
