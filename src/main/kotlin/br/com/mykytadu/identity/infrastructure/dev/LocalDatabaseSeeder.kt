package br.com.mykytadu.identity.infrastructure.dev

import br.com.mykytadu.identity.application.port.out.IdentityIdGenerator
import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.Role
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
@Profile("local")
internal class LocalDatabaseSeeder(
    private val accounts: UserAccountRepository,
    private val passwordHasher: PasswordHasher,
    private val idGenerator: IdentityIdGenerator,
    private val clock: Clock,
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(LocalDatabaseSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        val devEmail = Email.from(DEV_EMAIL)
        if (accounts.findByEmail(devEmail) != null) {
            logger.info("[LOCAL-DEV] Usuário de teste já presente no banco local: {}", DEV_EMAIL)
            return
        }

        val now = clock.instant()
        val userId = idGenerator.nextUserId()
        val user = User.pending(
            id = userId,
            email = devEmail,
            displayName = DEV_DISPLAY_NAME,
            now = now,
        ).verifyEmail(now)

        val passwordHash = passwordHasher.hash(DEV_PASSWORD)
        val credential = PasswordCredential.argon2id(userId, passwordHash, now)
        val roles = setOf(
            RoleAssignment(userId, Role.USER),
            RoleAssignment(userId, Role.ADMIN),
        )

        val account = UserAccount.restore(user, credential, roles)
        accounts.save(account)

        logger.info("[LOCAL-DEV] ===================================================")
        logger.info("[LOCAL-DEV] Usuário de teste semeado com sucesso!")
        logger.info("[LOCAL-DEV] ID:           {}", userId.value)
        logger.info("[LOCAL-DEV] E-mail:       {}", DEV_EMAIL)
        logger.info("[LOCAL-DEV] Senha:        {}", DEV_PASSWORD)
        logger.info("[LOCAL-DEV] Display Name: {}", DEV_DISPLAY_NAME)
        logger.info("[LOCAL-DEV] Roles:        USER, ADMIN")
        logger.info("[LOCAL-DEV] Status:       ACTIVE")
        logger.info("[LOCAL-DEV] ===================================================")
    }

    companion object {
        const val DEV_EMAIL = "dev@mykytadu.com"
        const val DEV_PASSWORD = "Password123!"
        const val DEV_DISPLAY_NAME = "Desenvolvedor Local"
    }
}
