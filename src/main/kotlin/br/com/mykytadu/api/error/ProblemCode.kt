package br.com.mykytadu.api.error

/**
 * Stable error identifiers exchanged at the HTTP boundary.
 *
 * The enum contains the codes already defined by the public contract. A code
 * must only be emitted by an adapter once the corresponding use case exists.
 */
enum class ProblemCode(val wireValue: String, val defaultTitle: String, val defaultDetail: String) {
    REQUEST_VALIDATION_FAILED(
        "request_validation_failed",
        "Request validation failed",
        "One or more fields are invalid.",
    ),
    REGISTRATION_REJECTED(
        "registration_rejected",
        "Registration unavailable",
        "The account cannot be created with the supplied data at this time.",
    ),
    INVALID_ACTION_TOKEN(
        "invalid_action_token",
        "Action token invalid",
        "The action token is invalid or expired.",
    ),
    AUTHENTICATION_REQUIRED(
        "authentication_required",
        "Authentication required",
        "A valid authentication credential is required.",
    ),
    INVALID_CREDENTIALS(
        "invalid_credentials",
        "Authentication failed",
        "The credentials could not be accepted.",
    ),
    EMAIL_VERIFICATION_REQUIRED(
        "email_verification_required",
        "Email verification required",
        "The account must be verified before login.",
    ),
    SESSION_INVALID(
        "session_invalid",
        "Session invalid",
        "The session is invalid, expired or no longer usable.",
    ),
    CSRF_INVALID(
        "csrf_invalid",
        "CSRF validation failed",
        "The CSRF token or origin could not be validated.",
    ),
    AUTHORIZATION_DENIED(
        "authorization_denied",
        "Access denied",
        "The authenticated principal is not authorized for this resource.",
    ),
    RATE_LIMIT_EXCEEDED(
        "rate_limit_exceeded",
        "Too many requests",
        "The operation is temporarily rate limited.",
    ),
    TRANSLATION_QUOTA_EXCEEDED(
        "translation_quota_exceeded",
        "Translation quota exceeded",
        "The translation limit for this principal was exceeded.",
    ),
    TRANSLATION_PROVIDER_FAILED(
        "translation_provider_failed",
        "Translation provider failed",
        "The translation provider could not complete the request.",
    ),
    TRANSLATION_PROVIDER_TIMEOUT(
        "translation_provider_timeout",
        "Translation provider timeout",
        "The translation provider did not respond in time.",
    ),
    INTERNAL_ERROR(
        "internal_error",
        "Internal error",
        "The request could not be completed.",
    ),
}
