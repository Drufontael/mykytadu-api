package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
import br.com.mykytadu.identity.domain.model.PasswordHash
import br.com.mykytadu.identity.domain.model.Role
import br.com.mykytadu.identity.domain.model.RoleAssignment
import br.com.mykytadu.identity.domain.model.User
import br.com.mykytadu.identity.domain.model.UserAccount
import br.com.mykytadu.identity.domain.model.UserId
import br.com.mykytadu.identity.domain.model.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration-test")
class UserAccountPersistenceIntegrationTests(
    @Autowired private val repository: UserAccountRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.action_tokens, identity.roles, identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `round trips the domain account through explicit persistence mappings`() {
        val account = UserAccount.pending(
            id = UserId.from(UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a901")),
            email = Email.from("Person@Example.COM"),
            displayName = "Person",
            passwordHash = PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=3,p=1${'$'}fixture"),
            now = Instant.parse("2026-09-18T12:00:00Z"),
        )

        repository.save(account)

        val restored = repository.findByEmail(Email.from(" person@example.com "))
        assertThat(restored).isNotNull
        assertThat(restored!!.user.id).isEqualTo(account.user.id)
        assertThat(restored.user.email.address).isEqualTo("Person@Example.COM")
        assertThat(restored.user.email.normalized).isEqualTo("person@example.com")
        assertThat(restored.user.status).isEqualTo(account.user.status)
        assertThat(restored.credential.algorithm).isEqualTo(account.credential.algorithm)
        assertThat(restored.credential.hash).isEqualTo(account.credential.hash)
        assertThat(restored.roles.map { it.role }).containsExactly(Role.USER)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity.roles WHERE user_id = ? AND role = 'USER'",
                Long::class.java,
                account.user.id.value,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `round trips every supported user status`() {
        val createdAt = Instant.parse("2026-09-18T12:00:00Z")
        val verifiedAt = createdAt.plusSeconds(60)
        val statuses = listOf(
            UserStatus.PENDING to null,
            UserStatus.ACTIVE to verifiedAt,
            UserStatus.BLOCKED to verifiedAt,
            UserStatus.DELETED to verifiedAt,
        )

        statuses.forEachIndexed { index, (status, emailVerifiedAt) ->
            val userId = UserId.from(
                UUID.fromString("01991f18-7d42-7b21-a2ef-1d8e6e14a90${index + 2}"),
            )
            val email = Email.from("status-$index@example.com")
            val user = User.restore(
                id = userId,
                email = email,
                displayName = "Status $index",
                status = status,
                emailVerifiedAt = emailVerifiedAt,
                createdAt = createdAt,
                updatedAt = verifiedAt,
            )
            val account = UserAccount.restore(
                user = user,
                credential = PasswordCredential.argon2id(
                    userId,
                    PasswordHash.from("${'$'}argon2id${'$'}v=19${'$'}m=65536,t=3,p=1${'$'}fixture-$index"),
                    createdAt,
                ),
                roles = setOf(RoleAssignment.defaultFor(userId)),
            )

            repository.save(account)

            val restored = repository.findByEmail(email)
            assertThat(restored).isNotNull
            assertThat(restored!!.user.status).isEqualTo(status)
            assertThat(restored.user.emailVerifiedAt).isEqualTo(emailVerifiedAt)
        }
    }

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
