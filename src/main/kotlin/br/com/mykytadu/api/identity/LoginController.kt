package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.IdentityLogin
import br.com.mykytadu.identity.api.InitialSession
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@Profile("!test")
@RequestMapping("/api/v1/auth")
internal class LoginController(private val login: IdentityLogin, private val requestKeyFactory: RequestKeyFactory) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        @RequestHeader(HttpHeaders.ORIGIN, required = false) origin: String?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<SessionResponse> {
        val outcome = login.login(
            LoginCommand(
                email = request.email,
                password = request.password,
                client = requireNotNull(AuthenticationClient.fromClientId(request.clientId)),
                requestKey = requestKeyFactory.from(servletRequest),
                origin = origin,
            ),
        )
        return when (outcome) {
            is LoginOutcome.Created -> outcome.session.toResponse()

            LoginOutcome.InvalidCredentials -> fail(HttpStatus.UNAUTHORIZED, ProblemCode.INVALID_CREDENTIALS)

            LoginOutcome.EmailVerificationRequired ->
                fail(HttpStatus.UNAUTHORIZED, ProblemCode.EMAIL_VERIFICATION_REQUIRED)

            LoginOutcome.InvalidOrigin -> fail(HttpStatus.FORBIDDEN, ProblemCode.CSRF_INVALID)

            is LoginOutcome.RateLimited ->
                fail(HttpStatus.TOO_MANY_REQUESTS, ProblemCode.RATE_LIMIT_EXCEEDED, outcome.retryAfterSeconds)
        }
    }

    private fun InitialSession.toResponse(): ResponseEntity<SessionResponse> {
        val body = SessionResponse(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = expiresInSeconds,
            refreshToken = refreshToken.takeUnless { client.web },
            csrfToken = csrfToken,
            user = principal.toResponse(),
        )
        val response = ResponseEntity.ok()
        if (client.web) {
            response.header(HttpHeaders.SET_COOKIE, refreshCookie(requireNotNull(refreshToken)).toString())
        }
        return response.body(body)
    }

    private fun InitialSession.refreshCookie(value: String): ResponseCookie = ResponseCookie
        .from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(Duration.ofSeconds(refreshExpiresInSeconds))
        .build()

    private fun AuthenticatedPrincipal.toResponse(): UserProfileResponse = UserProfileResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = "active",
        roles = roles,
        emailVerifiedAt = emailVerifiedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun fail(status: HttpStatus, code: ProblemCode, retryAfterSeconds: Long? = null): Nothing =
        throw ApiProblemException(status, code, retryAfterSeconds)

    companion object {
        private const val REFRESH_COOKIE = "mykytadu_refresh"
    }
}

internal data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val email: String,
    @field:Size(min = 1, max = 128)
    val password: String,
    @field:Pattern(regexp = "mykytadu-(web|android|ios|desktop)")
    val clientId: String? = null,
) {
    override fun toString(): String = "LoginRequest(email=[REDACTED], password=[REDACTED], clientId=$clientId)"
}

internal data class SessionResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String?,
    val csrfToken: String?,
    val user: UserProfileResponse,
)
