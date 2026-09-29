package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.IdentityLogout
import br.com.mykytadu.identity.api.LogoutAllSessionsCommand
import br.com.mykytadu.identity.api.LogoutOutcome
import br.com.mykytadu.identity.api.LogoutSessionCommand
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.UserId
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Profile("!test")
internal class SessionLogoutService(
    private val sessions: SessionStore,
    private val accounts: UserAccountRepository,
    private val tokens: SessionTokenCryptography,
    private val originPolicy: LoginOriginPolicy,
    private val clock: Clock,
    private val telemetry: AuthenticationTelemetry,
) : IdentityLogout {

    @Transactional
    override fun logout(command: LogoutSessionCommand): LogoutOutcome {
        val now = clock.instant()
        if (command.web && !originPolicy.isAllowed(command.origin)) return LogoutOutcome.CsrfInvalid
        return sessions.findByRefreshTokenHash(tokens.hash(command.refreshToken))
            ?.takeIf { it.userId.value == command.userId }
            ?.let { sessions.findSessionFamilyLocked(it, accounts) }
            ?.let { session ->
                when {
                    (session.clientId == Session.WEB_CLIENT_ID) != command.web -> LogoutOutcome.Completed

                    command.web && command.csrfToken?.let(tokens::hash) != session.csrfTokenHash ->
                        LogoutOutcome.CsrfInvalid

                    else -> {
                        if (sessions.revokeFamilyForLogout(session.tokenFamilyId, now) > 0) telemetry.sessionRevoked()
                        LogoutOutcome.Completed
                    }
                }
            }
            ?: LogoutOutcome.Completed
    }

    @Transactional
    override fun logoutAll(command: LogoutAllSessionsCommand): LogoutOutcome {
        val now = clock.instant()
        val userId = UserId.from(command.userId)
        val accountExists = accounts.findByIdForUpdate(userId) != null
        val originIsValid = !command.web || originPolicy.isAllowed(command.origin)
        val csrfIsValid = !command.web || validWebSession(command, userId)
        return when {
            !accountExists -> LogoutOutcome.Completed

            !originIsValid || !csrfIsValid -> LogoutOutcome.CsrfInvalid

            else -> {
                if (sessions.revokeAllForUser(userId, now) > 0) telemetry.sessionRevoked()
                LogoutOutcome.Completed
            }
        }
    }

    private fun validWebSession(command: LogoutAllSessionsCommand, userId: UserId): Boolean =
        command.currentRefreshToken
            ?.let { sessions.findByRefreshTokenHash(tokens.hash(it)) }
            ?.takeIf { it.userId == userId }
            ?.let { candidate ->
                sessions.findFamilyForUpdate(candidate.tokenFamilyId).firstOrNull { it.id == candidate.id }
            }
            ?.let { session ->
                session.clientId == Session.WEB_CLIENT_ID &&
                    command.csrfToken?.let(tokens::hash) == session.csrfTokenHash
            } ?: false
}
