package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.PasswordHasher
import br.com.mykytadu.identity.application.port.out.UserAccountRepository
import br.com.mykytadu.identity.domain.model.Email
import br.com.mykytadu.identity.domain.model.PasswordCredential
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
@SpringBootTest(
    properties = [
        "mykytadu.identity.registration.registration-limit=2",
        "mykytadu.identity.authentication.login-limit=2",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class IdentityAbuseIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accounts: UserAccountRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearIdentityData() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.action_tokens, identity.roles, " +
                "identity.password_credentials, identity.users",
        )
    }

    @Test
    fun `returns one stable rejection for concurrent duplicate registration`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attempts = listOf("198.51.100.10", "198.51.100.11").map { remoteAddress ->
                executor.submit<MvcResult> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "Concurrent registration did not start" }
                    register(CONCURRENT_EMAIL, remoteAddress)
                }
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            val responses = attempts.map { it.get(30, TimeUnit.SECONDS).response }
            assertThat(responses.map { it.status }).containsExactlyInAnyOrder(201, 409)
            val rejection = responses.single { it.status == 409 }
            assertThat(rejection.contentAsString)
                .contains("\"code\":\"registration_rejected\"")
                .doesNotContain(CONCURRENT_EMAIL, PASSWORD, "argon2", "actionToken")
            assertThat(count("identity.users")).isEqualTo(1)
            assertThat(count("identity.password_credentials")).isEqualTo(1)
            assertThat(count("identity.roles")).isEqualTo(1)
            assertThat(count("identity.action_tokens")).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `limits registration attempts with a stable retry response`() {
        assertThat(register("first-rate@example.test", "198.51.100.20").response.status).isEqualTo(201)
        assertThat(register("second-rate@example.test", "198.51.100.20").response.status).isEqualTo(201)

        val limited = register("third-rate@example.test", "198.51.100.20").response

        assertRateLimited(limited.status, limited.getHeader(HttpHeaders.RETRY_AFTER), limited.contentAsString)
        assertThat(limited.contentAsString).doesNotContain("third-rate@example.test", PASSWORD)
    }

    @Test
    fun `limits repeated invalid logins without blocking another origin`() {
        saveAccount("active-person@example.test", UserStatus.ACTIVE, 1)

        assertThat(login("wrong-password", "198.51.100.30").response.status).isEqualTo(401)
        assertThat(login("wrong-password", "198.51.100.30").response.status).isEqualTo(401)
        val limited = login(PASSWORD, "198.51.100.30").response

        assertRateLimited(limited.status, limited.getHeader(HttpHeaders.RETRY_AFTER), limited.contentAsString)
        assertThat(limited.contentAsString).doesNotContain("active-person@example.test", PASSWORD)
        assertThat(login(PASSWORD, "198.51.100.31").response.status).isEqualTo(200)
        assertThat(count("identity.sessions")).isEqualTo(1)
    }

    @Test
    fun `returns generic credentials failure for a blocked account without creating a session`() {
        saveAccount("blocked-person@example.test", UserStatus.BLOCKED, 2)

        val response = login(PASSWORD, "198.51.100.40", "blocked-person@example.test").response

        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentAsString)
            .contains("\"code\":\"invalid_credentials\"")
            .doesNotContain("blocked-person@example.test", PASSWORD, "blocked")
        assertThat(count("identity.sessions")).isZero()
    }

    private fun register(email: String, remoteAddress: String): MvcResult = mockMvc.perform(
        post("/api/v1/auth/register")
            .with { request -> request.apply { this.remoteAddr = remoteAddress } }
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$PASSWORD","displayName":"Person"}"""),
    ).andReturn()

    private fun login(
        password: String,
        remoteAddress: String,
        email: String = "active-person@example.test",
    ): MvcResult = mockMvc.perform(
        post("/api/v1/auth/login")
            .with { request -> request.apply { this.remoteAddr = remoteAddress } }
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"email":"$email","password":"$password","clientId":"mykytadu-android"}""",
            ),
    ).andReturn()

    private fun saveAccount(email: String, status: UserStatus, sequence: Int) {
        val id = UserId.from(UUID.fromString("019937b6-3600-7001-8000-0000000000${sequence}0"))
        val verifiedAt = NOW.plusSeconds(60)
        accounts.save(
            UserAccount.restore(
                user = User.restore(
                    id = id,
                    email = Email.from(email),
                    displayName = "Person",
                    status = status,
                    emailVerifiedAt = verifiedAt,
                    createdAt = NOW,
                    updatedAt = verifiedAt,
                ),
                credential = PasswordCredential.argon2id(id, passwordHasher.hash(PASSWORD), NOW),
                roles = setOf(RoleAssignment.defaultFor(id)),
            ),
        )
    }

    private fun assertRateLimited(status: Int, retryAfter: String?, body: String) {
        assertThat(status).isEqualTo(429)
        assertThat(requireNotNull(retryAfter).toLong()).isBetween(1, 600)
        assertThat(body).contains("\"code\":\"rate_limit_exceeded\"")
    }

    private fun count(table: String): Long =
        requireNotNull(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java))

    companion object {
        private val NOW = Instant.parse("2026-09-22T12:00:00Z")
        private const val CONCURRENT_EMAIL = "concurrent-person@example.test"
        private const val PASSWORD = "a-secure-test-password"

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSqlIntegrationFixture.newContainer()
    }
}
