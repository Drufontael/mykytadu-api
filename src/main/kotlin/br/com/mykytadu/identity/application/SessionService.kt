package br.com.mykytadu.identity.application

import br.com.mykytadu.identity.api.AuthenticatedPrincipal
import br.com.mykytadu.identity.api.AuthenticationClient
import br.com.mykytadu.identity.api.CsrfOutcome
import br.com.mykytadu.identity.api.IdentitySession
import br.com.mykytadu.identity.api.RefreshOutcome
import br.com.mykytadu.identity.api.RefreshSessionCommand
import br.com.mykytadu.identity.api.RefreshedSession
import br.com.mykytadu.identity.api.ReissueCsrfCommand
import br.com.mykytadu.identity.application.port.out.AccessTokenIssuer
import br.com.mykytadu.identity.application.port.out.AuthenticationTelemetry
import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy
import br.com.mykytadu.identity.application.port.out.RefreshTokenDeriver
import br.com.mykytadu.identity.application.port.out.SessionIdGenerator
import br.com.mykytadu.identity.application.port.out.SessionStore
import br.com.mykytadu.identity.application.port.out.SessionTokenCryptography
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Session
import br.com.mykytadu.identity.domain.model.SessionId
import br.com.mykytadu.identity.domain.model.SessionRotationReplay
import br.com.mykytadu.identity.domain.model.UserAccount
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
@Profile("!test")
internal class SessionService(
    private val sessions: SessionStore,
    private val accounts: UserAccountRepository,
    private val telemetry: AuthenticationTelemetry,
    private val ids: SessionIdGenerator,
    private val tokens: SessionTokenCryptography,
    private val refreshDeriver: RefreshTokenDeriver,
    private val accessTokens: AccessTokenIssuer,
    private val originPolicy: LoginOriginPolicy,
    private val clock: Clock,
    private val properties: SessionApplicationProperties,
) : IdentitySession {

    @Transactional
    override fun reissueCsrf(command: ReissueCsrfCommand): CsrfOutcome {
        val now = clock.instant()
        val session = sessions.findSessionFamilyLocked(command.refreshToken, tokens, accounts)
        return if (session == null || !session.isActiveAt(now) || session.clientId != Session.WEB_CLIENT_ID) {
            CsrfOutcome.SessionInvalid
        } else {
            val csrfToken = tokens.generateToken()
            sessions.update(session.reissueCsrf(tokens.hash(csrfToken)))
            CsrfOutcome.Issued(csrfToken)
        }
    }

    @Transactional
    override fun refresh(command: RefreshSessionCommand): RefreshOutcome {
        val now = clock.instant()
        val predecessor = sessions.findSessionFamilyLocked(command.refreshToken, tokens, accounts)
        return predecessor?.let { refreshKnown(it, command, now) } ?: RefreshOutcome.SessionInvalid
    }

    private fun refreshKnown(
        predecessor: Session,
        command: RefreshSessionCommand,
        now: java.time.Instant,
    ): RefreshOutcome {
        val client = AuthenticationClient.fromClientId(predecessor.clientId)
        return when {
            client == null || command.web != client.web -> RefreshOutcome.SessionInvalid

            predecessor.revokeReason == Session.ROTATED_REASON ->
                replay(predecessor, client, command, now)

            !predecessor.isActiveAt(now) -> RefreshOutcome.SessionInvalid

            client.web && !validWebRequest(predecessor, command) -> RefreshOutcome.CsrfInvalid

            else -> rotate(predecessor, client, command, now)
        }
    }

    private fun rotate(
        predecessor: Session,
        client: AuthenticationClient,
        command: RefreshSessionCommand,
        now: java.time.Instant,
    ): RefreshOutcome {
        val principal = accounts.findById(predecessor.userId)?.toPrincipal()
        val successorId = ids.nextSessionId()
        val effectiveKey = command.idempotencyKey ?: tokens.generateToken()
        val refreshToken = refreshDeriver.derive(
            refreshDeriver.activeKeyId,
            predecessor.id,
            successorId,
            effectiveKey,
        )
        return if (principal == null || refreshToken == null) {
            RefreshOutcome.SessionInvalid
        } else {
            rotateResolved(predecessor, client, now, principal, successorId, refreshToken, effectiveKey)
        }
    }

    private fun rotateResolved(
        predecessor: Session,
        client: AuthenticationClient,
        now: java.time.Instant,
        principal: AuthenticatedPrincipal,
        successorId: SessionId,
        refreshToken: String,
        effectiveKey: String,
    ): RefreshOutcome {
        val csrfToken = tokens.generateToken().takeIf { client.web }
        val accessIssuedAt = now
        val accessExpiresAt = now.plus(properties.accessTokenTtl)
        val replay = SessionRotationReplay(
            successorSessionId = successorId,
            idempotencyKeyHash = tokens.hash(effectiveKey),
            derivationKeyId = refreshDeriver.activeKeyId,
            replayUntil = now.plus(properties.replayWindow),
            accessTokenId = ids.nextAccessTokenId(),
            accessIssuedAt = accessIssuedAt,
            accessExpiresAt = accessExpiresAt,
        )
        val successor = Session.successor(
            id = successorId,
            predecessorId = predecessor.id,
            userId = predecessor.userId,
            refreshTokenHash = tokens.hash(refreshToken),
            tokenFamilyId = predecessor.tokenFamilyId,
            clientId = predecessor.clientId,
            csrfTokenHash = csrfToken?.let(tokens::hash),
            createdAt = now,
            expiresAt = now.plus(properties.refreshTokenTtl),
        )
        sessions.create(successor)
        sessions.update(predecessor.markRotated(now, replay))
        return refreshed(principal, client, refreshToken, csrfToken, replay)
    }

    private fun replay(
        predecessor: Session,
        client: AuthenticationClient,
        command: RefreshSessionCommand,
        now: java.time.Instant,
    ): RefreshOutcome = when {
        client.web && !validWebRequest(predecessor, command) -> RefreshOutcome.CsrfInvalid

        command.idempotencyKey == null ||
            !predecessor.isReplayAllowed(tokens.hash(command.idempotencyKey), now) -> detectReuse(predecessor, now)

        else -> replayAllowed(predecessor, client, command)
    }

    private fun detectReuse(predecessor: Session, now: java.time.Instant): RefreshOutcome {
        sessions.revokeRenewableFamily(predecessor.tokenFamilyId, now)
        telemetry.refreshReuseDetected()
        return RefreshOutcome.SessionInvalid
    }

    private fun replayAllowed(
        predecessor: Session,
        client: AuthenticationClient,
        command: RefreshSessionCommand,
    ): RefreshOutcome {
        val replay = requireNotNull(predecessor.rotationReplay)
        val refreshToken = refreshDeriver.derive(
            replay.derivationKeyId,
            predecessor.id,
            replay.successorSessionId,
            requireNotNull(command.idempotencyKey),
        )
        val successor = sessions.findById(replay.successorSessionId)
        val principal = accounts.findById(predecessor.userId)?.toPrincipal()
        return if (refreshToken == null || successor == null || principal == null) {
            RefreshOutcome.SessionInvalid
        } else {
            val csrfToken = tokens.generateToken().takeIf { client.web }
            if (csrfToken != null) sessions.update(successor.reissueCsrf(tokens.hash(csrfToken)))
            refreshed(principal, client, refreshToken, csrfToken, replay)
        }
    }

    private fun validWebRequest(predecessor: Session, command: RefreshSessionCommand): Boolean =
        originPolicy.isAllowed(command.origin) &&
            command.csrfToken?.let(tokens::hash) == predecessor.csrfTokenHash

    private fun refreshed(
        principal: AuthenticatedPrincipal,
        client: AuthenticationClient,
        refreshToken: String,
        csrfToken: String?,
        replay: SessionRotationReplay,
    ): RefreshOutcome = RefreshOutcome.Refreshed(
        RefreshedSession(
            accessToken = accessTokens.issue(
                principal,
                client,
                replay.accessTokenId,
                replay.accessIssuedAt,
                replay.accessExpiresAt,
            ),
            expiresInSeconds = properties.accessTokenTtl.seconds,
            refreshExpiresInSeconds = properties.refreshTokenTtl.seconds,
            refreshToken = refreshToken,
            csrfToken = csrfToken,
            principal = principal,
            client = client,
        ),
    )

    private fun UserAccount.toPrincipal(): AuthenticatedPrincipal = AuthenticatedPrincipal(
        id = user.id.value,
        email = user.email.address,
        displayName = user.displayName,
        roles = roles.mapTo(linkedSetOf()) { it.role.name },
        emailVerifiedAt = requireNotNull(user.emailVerifiedAt),
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}

data class SessionApplicationProperties(
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
    val replayWindow: Duration,
)
