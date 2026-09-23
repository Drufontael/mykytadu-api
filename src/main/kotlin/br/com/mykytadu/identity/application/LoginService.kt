package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticateCommand
import br.com.mykytadu.identity.api.AuthenticationOutcome
import br.com.mykytadu.identity.api.IdentityAuthentication
import br.com.mykytadu.identity.api.IdentityLogin
import br.com.mykytadu.identity.api.InitialSession
import br.com.mykytadu.identity.api.LoginCommand
import br.com.mykytadu.identity.api.LoginOutcome
import br.com.mykytadu.identity.application.port.out.AccessTokenIssuer
import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy
import br.com.mykytadu.identity.application.port.out.SessionIdGenerator
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.UserId
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
@Profile("!test")
internal class LoginService(
    private val authentication: IdentityAuthentication,
    private val sessions: SessionStore,
    private val idGenerator: SessionIdGenerator,
    private val tokenCryptography: SessionTokenCryptography,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val originPolicy: LoginOriginPolicy,
    private val clock: Clock,
    private val properties: LoginApplicationProperties,
) : IdentityLogin {

    override fun login(command: LoginCommand): LoginOutcome {
        val authenticationOutcome = authentication.authenticate(
            AuthenticateCommand(command.email, command.password, command.client, command.requestKey),
        )
        return when (authenticationOutcome) {
            is AuthenticationOutcome.Authenticated -> createSession(authenticationOutcome, command)
            AuthenticationOutcome.InvalidCredentials -> LoginOutcome.InvalidCredentials
            AuthenticationOutcome.EmailVerificationRequired -> LoginOutcome.EmailVerificationRequired
            is AuthenticationOutcome.RateLimited -> LoginOutcome.RateLimited(authenticationOutcome.retryAfterSeconds)
        }
    }

    private fun createSession(
        authenticated: AuthenticationOutcome.Authenticated,
        command: LoginCommand,
    ): LoginOutcome {
        if (authenticated.client.web && !originPolicy.isAllowed(command.origin)) {
            return LoginOutcome.InvalidOrigin
        }
        val now = clock.instant()
        val refreshToken = tokenCryptography.generateToken()
        val csrfToken = tokenCryptography.generateToken().takeIf { authenticated.client.web }
        val expiresAt = now.plus(properties.refreshTokenTtl)
        sessions.create(
            Session.initial(
                id = idGenerator.nextSessionId(),
                userId = UserId.from(authenticated.principal.id),
                refreshTokenHash = tokenCryptography.hash(refreshToken),
                tokenFamilyId = idGenerator.nextTokenFamilyId(),
                clientId = authenticated.client.clientId,
                csrfTokenHash = csrfToken?.let(tokenCryptography::hash),
                createdAt = now,
                expiresAt = expiresAt,
            ),
        )
        val accessExpiresAt = now.plus(properties.accessTokenTtl)
        val accessToken = accessTokenIssuer.issue(
            authenticated.principal,
            authenticated.client,
            idGenerator.nextAccessTokenId(),
            now,
            accessExpiresAt,
        )
        return LoginOutcome.Created(
            InitialSession(
                accessToken = accessToken,
                expiresInSeconds = properties.accessTokenTtl.seconds,
                refreshExpiresInSeconds = properties.refreshTokenTtl.seconds,
                refreshToken = refreshToken,
                csrfToken = csrfToken,
                principal = authenticated.principal,
                client = authenticated.client,
            ),
        )
    }
}

data class LoginApplicationProperties(val accessTokenTtl: Duration, val refreshTokenTtl: Duration)
