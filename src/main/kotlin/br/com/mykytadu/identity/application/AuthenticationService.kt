package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationOutcome
import br.com.mykytadu.identity.api.IdentityAuthentication
import br.com.mykytadu.identity.application.port.out.AuthenticationRateLimiter
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.application.port.out.PasswordVerifier
import br.com.mykytadu.identity.application.port.out.RateLimitDecision
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test")
internal class AuthenticationService(
    private val accounts: UserAccountRepository,
    private val passwordVerifier: PasswordVerifier,
    private val rateLimiter: AuthenticationRateLimiter,
    private val telemetry: AuthenticationTelemetry,
) : IdentityAuthentication {

    override fun authenticate(command: AuthenticateCommand): AuthenticationOutcome =
        when (val decision = rateLimiter.acquireLogin(command.requestKey)) {
            RateLimitDecision.Allowed -> authenticateAllowed(command)
            is RateLimitDecision.Rejected -> rateLimited(decision.retryAfterSeconds)
        }

    private fun authenticateAllowed(command: AuthenticateCommand): AuthenticationOutcome {
        val account = accounts.findByEmail(Email.from(command.email))
        val credentialsMatch = passwordVerifier.matches(command.password, account?.credential?.hash)
        return when {
            !credentialsMatch || account == null -> invalidCredentials()
            account.user.status == UserStatus.PENDING -> emailVerificationRequired()
            account.user.status != UserStatus.ACTIVE -> invalidCredentials()
            else -> authenticated(account, command)
        }
    }

    private fun authenticated(account: UserAccount, command: AuthenticateCommand): AuthenticationOutcome {
        telemetry.accepted()
        return AuthenticationOutcome.Authenticated(
            principal = AuthenticatedPrincipal(
                id = account.user.id.value,
                email = account.user.email.address,
                displayName = account.user.displayName,
                roles = account.roles.mapTo(linkedSetOf()) { it.role.name },
                emailVerifiedAt = requireNotNull(account.user.emailVerifiedAt),
                createdAt = account.user.createdAt,
                updatedAt = account.user.updatedAt,
            ),
            client = command.client,
        )
    }

    private fun invalidCredentials(): AuthenticationOutcome {
        telemetry.invalidCredentials()
        return AuthenticationOutcome.InvalidCredentials
    }

    private fun emailVerificationRequired(): AuthenticationOutcome {
        telemetry.emailVerificationRequired()
        return AuthenticationOutcome.EmailVerificationRequired
    }

    private fun rateLimited(retryAfterSeconds: Long): AuthenticationOutcome {
        telemetry.rateLimited()
        return AuthenticationOutcome.RateLimited(retryAfterSeconds)
    }
}
