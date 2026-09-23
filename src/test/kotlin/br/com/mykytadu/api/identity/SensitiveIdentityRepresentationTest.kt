package br.com.mykytadu.api.identity

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.InitialSession
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegisteredUser
import br.com.mykytadu.identity.api.RegistrationOutcome
import br.com.mykytadu.identity.api.ResendVerificationCommand
import br.com.mykytadu.identity.api.VerifyEmailCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SensitiveIdentityRepresentationTest {

    @Test
    fun `redacts credentials tokens and personal data from identity representations`() {
        val representations = (sensitiveInputs() + sensitiveOutputs()).joinToString(separator = "\n")

        assertThat(representations)
            .contains("[REDACTED]")
            .doesNotContain(
                USER_ID.toString(),
                EMAIL,
                DISPLAY_NAME,
                PASSWORD,
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                CSRF_TOKEN,
                ACTION_TOKEN,
                REQUEST_KEY,
                ORIGIN,
            )
    }

    private fun sensitiveInputs(): List<Any> = listOf(
        RegisterRequest(EMAIL, PASSWORD, DISPLAY_NAME),
        ResendVerificationRequest(EMAIL),
        VerifyEmailRequest(ACTION_TOKEN),
        LoginRequest(EMAIL, PASSWORD, AuthenticationClient.WEB.clientId),
        RegisterAccountCommand(EMAIL, PASSWORD, DISPLAY_NAME, REQUEST_KEY),
        ResendVerificationCommand(EMAIL, REQUEST_KEY),
        VerifyEmailCommand(ACTION_TOKEN),
        AuthenticateCommand(EMAIL, PASSWORD, AuthenticationClient.WEB, REQUEST_KEY),
        LoginCommand(EMAIL, PASSWORD, AuthenticationClient.WEB, REQUEST_KEY, ORIGIN),
    )

    private fun sensitiveOutputs(): List<Any> {
        val principal = principal()
        val initialSession = InitialSession(
            accessToken = ACCESS_TOKEN,
            expiresInSeconds = 600,
            refreshExpiresInSeconds = 2_592_000,
            refreshToken = REFRESH_TOKEN,
            csrfToken = CSRF_TOKEN,
            principal = principal,
            client = AuthenticationClient.WEB,
        )
        val user = UserProfileResponse(
            id = USER_ID,
            email = EMAIL,
            displayName = DISPLAY_NAME,
            status = "active",
            roles = setOf("USER"),
            emailVerifiedAt = NOW,
            createdAt = NOW,
            updatedAt = NOW,
        )
        val registered = RegisteredUser(
            id = USER_ID,
            email = EMAIL,
            displayName = DISPLAY_NAME,
            status = "pending",
            roles = setOf("USER"),
            emailVerifiedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        )
        return listOf(
            principal,
            initialSession,
            LoginOutcome.Created(initialSession),
            SessionResponse(ACCESS_TOKEN, "Bearer", 600, REFRESH_TOKEN, CSRF_TOKEN, user),
            user,
            RegistrationResponse(user, "verifyEmail"),
            registered,
            RegistrationOutcome.Created(registered),
        )
    }

    private fun principal() = AuthenticatedPrincipal(
        id = USER_ID,
        email = EMAIL,
        displayName = DISPLAY_NAME,
        roles = setOf("USER"),
        emailVerifiedAt = NOW,
        createdAt = NOW,
        updatedAt = NOW,
    )

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private val USER_ID = UUID.fromString("019937b6-3600-7001-8000-000000000099")
        private const val EMAIL = "sensitive-person@example.test"
        private const val DISPLAY_NAME = "Sensitive Person"
        private const val PASSWORD = "sensitive-password"
        private const val ACCESS_TOKEN = "sensitive-access-token"
        private const val REFRESH_TOKEN = "sensitive-refresh-token"
        private const val CSRF_TOKEN = "sensitive-csrf-token"
        private const val ACTION_TOKEN = "sensitive-action-token"
        private const val REQUEST_KEY = "sensitive-request-key"
        private const val ORIGIN = "https://sensitive-origin.example.test"
    }
}
