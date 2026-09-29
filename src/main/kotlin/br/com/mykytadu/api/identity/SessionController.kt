package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.CsrfOutcome
import br.com.mykytadu.identity.api.IdentityLogout
import br.com.mykytadu.identity.api.IdentitySession
import br.com.mykytadu.identity.api.LogoutAllSessionsCommand
import br.com.mykytadu.identity.api.LogoutOutcome
import br.com.mykytadu.identity.api.LogoutSessionCommand
import br.com.mykytadu.identity.api.RefreshOutcome
import br.com.mykytadu.identity.api.RefreshSessionCommand
import br.com.mykytadu.identity.api.RefreshedSession
import br.com.mykytadu.identity.api.ReissueCsrfCommand
import br.com.mykytadu.identity.domain.model.Session
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@Profile("!test")
@RequestMapping("/api/v1/auth")
internal class SessionController(private val sessions: IdentitySession, private val logout: IdentityLogout) {

    @GetMapping("/csrf")
    fun csrf(@CookieValue(REFRESH_COOKIE, required = false) refreshToken: String?): CsrfResponse =
        when (val outcome = sessions.reissueCsrf(ReissueCsrfCommand(refreshToken ?: invalidSession()))) {
            is CsrfOutcome.Issued -> CsrfResponse(outcome.csrfToken)
            CsrfOutcome.SessionInvalid -> fail(HttpStatus.UNAUTHORIZED, ProblemCode.SESSION_INVALID)
        }

    @PostMapping("/refresh")
    fun refresh(
        @RequestHeader(IDEMPOTENCY_KEY, required = false) @Size(max = 128) idempotencyKey: String?,
        @RequestHeader(HttpHeaders.ORIGIN, required = false) origin: String?,
        @RequestHeader(CSRF_HEADER, required = false) csrfToken: String?,
        @CookieValue(REFRESH_COOKIE, required = false) cookieRefreshToken: String?,
        @Valid @RequestBody(required = false) request: RefreshRequest?,
    ): ResponseEntity<SessionResponse> {
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        if ((cookieRefreshToken == null) == (request?.refreshToken == null)) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        val web = cookieRefreshToken != null
        val outcome = sessions.refresh(
            RefreshSessionCommand(
                refreshToken = cookieRefreshToken ?: requireNotNull(request?.refreshToken),
                idempotencyKey = idempotencyKey,
                csrfToken = csrfToken,
                origin = origin,
                web = web,
            ),
        )
        return when (outcome) {
            is RefreshOutcome.Refreshed -> outcome.session.toResponse()
            RefreshOutcome.SessionInvalid -> fail(HttpStatus.UNAUTHORIZED, ProblemCode.SESSION_INVALID)
            RefreshOutcome.CsrfInvalid -> fail(HttpStatus.FORBIDDEN, ProblemCode.CSRF_INVALID)
        }
    }

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: Jwt,
        @RequestHeader(HttpHeaders.ORIGIN, required = false) origin: String?,
        @RequestHeader(CSRF_HEADER, required = false) csrfToken: String?,
        @CookieValue(REFRESH_COOKIE, required = false) cookieRefreshToken: String?,
        @Valid @RequestBody(required = false) request: LogoutRequest?,
    ): ResponseEntity<Void> {
        if ((cookieRefreshToken == null) == (request?.refreshToken == null)) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        val web = cookieRefreshToken != null
        val outcome = logout.logout(
            LogoutSessionCommand(
                userId = UUID.fromString(principal.subject),
                refreshToken = cookieRefreshToken ?: requireNotNull(request?.refreshToken),
                csrfToken = csrfToken,
                origin = origin,
                web = web,
            ),
        )
        return when (outcome) {
            LogoutOutcome.Completed -> noContent(web)
            LogoutOutcome.CsrfInvalid -> fail(HttpStatus.FORBIDDEN, ProblemCode.CSRF_INVALID)
        }
    }

    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal principal: Jwt,
        @RequestHeader(HttpHeaders.ORIGIN, required = false) origin: String?,
        @RequestHeader(CSRF_HEADER, required = false) csrfToken: String?,
        @CookieValue(REFRESH_COOKIE, required = false) cookieRefreshToken: String?,
    ): ResponseEntity<Void> {
        val web = principal.getClaimAsString(CLIENT_ID_CLAIM) == Session.WEB_CLIENT_ID
        val outcome = logout.logoutAll(
            LogoutAllSessionsCommand(
                userId = UUID.fromString(principal.subject),
                currentRefreshToken = cookieRefreshToken,
                csrfToken = csrfToken,
                origin = origin,
                web = web,
            ),
        )
        return when (outcome) {
            LogoutOutcome.Completed -> noContent(web)
            LogoutOutcome.CsrfInvalid -> fail(HttpStatus.FORBIDDEN, ProblemCode.CSRF_INVALID)
        }
    }

    private fun noContent(clearWebCookie: Boolean): ResponseEntity<Void> {
        val response = ResponseEntity.noContent()
        if (clearWebCookie) response.header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
        return response.build()
    }

    private fun RefreshedSession.toResponse(): ResponseEntity<SessionResponse> {
        val response = ResponseEntity.ok()
        if (client.web) {
            response.header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, refreshExpiresInSeconds).toString())
        }
        return response.body(
            SessionResponse(
                accessToken = accessToken,
                tokenType = "Bearer",
                expiresIn = expiresInSeconds,
                refreshToken = refreshToken.takeUnless { client.web },
                csrfToken = csrfToken,
                user = principal.toResponse(),
            ),
        )
    }

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

    private fun refreshCookie(value: String, maxAgeSeconds: Long): ResponseCookie = ResponseCookie
        .from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(Duration.ofSeconds(maxAgeSeconds))
        .build()

    private fun clearedRefreshCookie(): ResponseCookie = ResponseCookie
        .from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(Duration.ZERO)
        .build()

    private fun fail(status: HttpStatus, code: ProblemCode): Nothing = throw ApiProblemException(status, code)

    private fun invalidSession(): Nothing = fail(HttpStatus.UNAUTHORIZED, ProblemCode.SESSION_INVALID)

    companion object {
        private const val REFRESH_COOKIE = "mykytadu_refresh"
        private const val IDEMPOTENCY_KEY = "Idempotency-Key"
        private const val CSRF_HEADER = "X-CSRF-Token"
        private const val CLIENT_ID_CLAIM = "client_id"
    }
}

internal data class RefreshRequest(
    @field:NotBlank
    @field:Size(max = 1024)
    val refreshToken: String,
) {
    override fun toString(): String = "RefreshRequest(refreshToken=[REDACTED])"
}

internal data class LogoutRequest(
    @field:NotBlank
    @field:Size(max = 1024)
    val refreshToken: String,
) {
    override fun toString(): String = "LogoutRequest(refreshToken=[REDACTED])"
}

internal data class CsrfResponse(val csrfToken: String) {
    override fun toString(): String = "CsrfResponse(csrfToken=[REDACTED])"
}
