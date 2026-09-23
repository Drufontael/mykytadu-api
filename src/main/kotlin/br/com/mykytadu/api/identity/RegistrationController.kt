package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.identity.api.EmailVerificationOutcome
import br.com.mykytadu.identity.api.IdentityRegistration
import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegisteredUser
import br.com.mykytadu.identity.api.RegistrationOutcome
import br.com.mykytadu.identity.api.ResendVerificationCommand
import br.com.mykytadu.identity.api.ResendVerificationOutcome
import br.com.mykytadu.identity.api.VerifyEmailCommand
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@Profile("!test")
@RequestMapping("/api/v1/auth")
internal class RegistrationController(
    private val registration: IdentityRegistration,
    private val requestKeyFactory: RequestKeyFactory,
) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<RegistrationResponse> {
        val outcome = registration.register(
            RegisterAccountCommand(
                email = request.email,
                password = request.password,
                displayName = request.displayName,
                requestKey = requestKeyFactory.from(servletRequest),
            ),
        )
        return when (outcome) {
            is RegistrationOutcome.Created -> ResponseEntity.status(HttpStatus.CREATED)
                .body(RegistrationResponse(outcome.user.toResponse(), "verifyEmail"))

            RegistrationOutcome.Rejected -> fail(
                HttpStatus.CONFLICT,
                ProblemCode.REGISTRATION_REJECTED,
            )

            is RegistrationOutcome.DeliveryUnavailable -> fail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemCode.EMAIL_DELIVERY_UNAVAILABLE,
                outcome.retryAfterSeconds,
            )

            is RegistrationOutcome.RateLimited -> fail(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemCode.RATE_LIMIT_EXCEEDED,
                outcome.retryAfterSeconds,
            )
        }
    }

    @PostMapping("/verify-email/resend")
    fun resendVerification(
        @Valid @RequestBody request: ResendVerificationRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ActionAcceptedResponse> {
        val outcome = registration.resendVerification(
            ResendVerificationCommand(
                email = request.email,
                requestKey = requestKeyFactory.from(servletRequest),
            ),
        )
        return when (outcome) {
            ResendVerificationOutcome.Accepted -> ResponseEntity.accepted().body(ActionAcceptedResponse(true))

            is ResendVerificationOutcome.RateLimited -> fail(
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemCode.RATE_LIMIT_EXCEEDED,
                outcome.retryAfterSeconds,
            )
        }
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<Void> =
        when (registration.verifyEmail(VerifyEmailCommand(request.actionToken))) {
            EmailVerificationOutcome.Verified -> ResponseEntity.noContent().build()
            EmailVerificationOutcome.Invalid -> fail(HttpStatus.BAD_REQUEST, ProblemCode.INVALID_ACTION_TOKEN)
        }

    private fun RegisteredUser.toResponse(): UserProfileResponse = UserProfileResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = status,
        roles = roles,
        emailVerifiedAt = emailVerifiedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun fail(status: HttpStatus, code: ProblemCode, retryAfterSeconds: Long? = null): Nothing =
        throw ApiProblemException(status, code, retryAfterSeconds)
}

internal data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,
    @field:NotBlank
    @field:Size(min = 12, max = 128)
    val password: String,
    @field:Size(min = 1, max = 80)
    @field:Pattern(regexp = ".*\\S.*", message = "must contain a non-whitespace character")
    val displayName: String? = null,
) {

    override fun toString(): String = "RegisterRequest(email=[REDACTED], password=[REDACTED])"
}

internal data class ResendVerificationRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,
) {

    override fun toString(): String = "ResendVerificationRequest(email=[REDACTED])"
}

internal data class VerifyEmailRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val actionToken: String,
) {

    override fun toString(): String = "VerifyEmailRequest(actionToken=[REDACTED])"
}

internal data class RegistrationResponse(val user: UserProfileResponse, val nextAction: String)

internal data class UserProfileResponse(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val status: String,
    val roles: Set<String>,
    val emailVerifiedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    override fun toString(): String =
        "UserProfileResponse(id=[REDACTED], email=[REDACTED], displayName=[REDACTED], status=$status, roles=$roles)"
}

internal data class ActionAcceptedResponse(val accepted: Boolean)
