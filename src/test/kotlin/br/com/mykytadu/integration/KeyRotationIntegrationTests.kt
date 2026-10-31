package br.com.mykytadu.integration

import br.com.mykytadu.identity.application.port.out.RefreshTokenDeriver
import br.com.mykytadu.identity.domain.model.SessionId
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
internal class KeyRotationIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val refreshTokenDeriver: RefreshTokenDeriver,
) {

    private val testUserId = UUID.fromString("0192e22a-0000-7000-8000-000000000001")

    @BeforeEach
    fun setupTestUser() {
        jdbcTemplate.update(
            "TRUNCATE TABLE identity.sessions, identity.roles, identity.password_credentials, identity.users CASCADE",
        )
        jdbcTemplate.update(
            "INSERT INTO identity.users " +
                "(id, email, normalized_email, status, email_verified_at, created_at, updated_at) " +
                "VALUES (?, 'rotation-user@example.com', 'rotation-user@example.com', 'active', NOW(), NOW(), NOW())",
            testUserId,
        )
        jdbcTemplate.update(
            "INSERT INTO identity.password_credentials (user_id, password_hash, algorithm, updated_at) " +
                "VALUES (?, '\$argon2id\$v=19\$m=65536,t=3,p=1\$fake\$fake', 'argon2id', NOW())",
            testUserId,
        )
        jdbcTemplate.update(
            "INSERT INTO identity.roles (user_id, role) VALUES (?, 'USER')",
            testUserId,
        )
    }

    @Test
    fun `access token signed with previous key is accepted without downtime`() {
        val previousToken = createSignedJwt(
            keyId = PREVIOUS_KEY_ID,
            privateKey = previousKeyPair.private,
            userId = testUserId,
        )

        mockMvc.perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $previousToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
            .andExpect(jsonPath("$.email").value("rotation-user@example.com"))
    }

    @Test
    fun `access token signed with active key is accepted normally`() {
        val activeToken = createSignedJwt(
            keyId = ACTIVE_KEY_ID,
            privateKey = activeKeyPair.private,
            userId = testUserId,
        )

        mockMvc.perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $activeToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testUserId.toString()))
    }

    @Test
    fun `access token signed with unknown key id is rejected with 401`() {
        val unknownToken = createSignedJwt(
            keyId = "unknown-key-9999",
            privateKey = activeKeyPair.private,
            userId = testUserId,
        )

        mockMvc.perform(
            get("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $unknownToken"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("authentication_required"))
    }

    @Test
    fun `refresh token derivation validates tokens from both active and previous hmac keys`() {
        val predecessor = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000010"))
        val successor = SessionId.from(UUID.fromString("019937b6-3600-7001-8000-000000000011"))
        val idempotency = "test-idempotency-key"

        val previousDerived = refreshTokenDeriver.derive(PREVIOUS_HMAC_KEY_ID, predecessor, successor, idempotency)
        assertThat(previousDerived).isNotNull

        val activeDerived = refreshTokenDeriver.derive(ACTIVE_HMAC_KEY_ID, predecessor, successor, idempotency)
        assertThat(activeDerived).isNotNull

        assertThat(refreshTokenDeriver.activeKeyId).isEqualTo(ACTIVE_HMAC_KEY_ID)

        val unknownDerived = refreshTokenDeriver.derive("unknown-hmac-key", predecessor, successor, idempotency)
        assertThat(unknownDerived).isNull()
    }

    private fun createSignedJwt(keyId: String, privateKey: PrivateKey, userId: UUID): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(userId.toString())
            .issuer("https://api.mykytadu.com")
            .audience("mykytadu-api")
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(900)))
            .jwtID(UUID.randomUUID().toString())
            .claim("roles", listOf("USER"))
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(keyId)
            .build()

        return SignedJWT(header, claims).apply { sign(RSASSASigner(privateKey)) }.serialize()
    }

    companion object {
        private const val ACTIVE_KEY_ID = "active-rsa-202611"
        private const val PREVIOUS_KEY_ID = "previous-rsa-202610"
        private const val ACTIVE_HMAC_KEY_ID = "active-hmac-202611"
        private const val PREVIOUS_HMAC_KEY_ID = "previous-hmac-202610"

        private val activeKeyPair: KeyPair = generateRsaKeyPair()
        private val previousKeyPair: KeyPair = generateRsaKeyPair()
        private val activeHmacKeyBase64: String = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        private val previousHmacKeyBase64: String = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("mykytadu_test")
            .withUsername("mykytadu_test")
            .withPassword("mykytadu_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("mykytadu.identity.jwt.key-mode") { "configured" }
            registry.add("mykytadu.identity.jwt.active-key-id") { ACTIVE_KEY_ID }
            registry.add("mykytadu.identity.jwt.private-key-pem") { toPem(activeKeyPair.private, "PRIVATE KEY") }
            registry.add("mykytadu.identity.jwt.public-key-pem") { toPem(activeKeyPair.public, "PUBLIC KEY") }
            registry.add("mykytadu.identity.jwt.previous-key-id") { PREVIOUS_KEY_ID }
            registry.add("mykytadu.identity.jwt.previous-public-key-pem") {
                toPem(previousKeyPair.public, "PUBLIC KEY")
            }

            registry.add("mykytadu.identity.refresh-derivation.key-mode") { "configured" }
            registry.add("mykytadu.identity.refresh-derivation.active-key-id") { ACTIVE_HMAC_KEY_ID }
            registry.add("mykytadu.identity.refresh-derivation.active-key-base64") { activeHmacKeyBase64 }
            registry.add("mykytadu.identity.refresh-derivation.previous-key-id") { PREVIOUS_HMAC_KEY_ID }
            registry.add("mykytadu.identity.refresh-derivation.previous-key-base64") { previousHmacKeyBase64 }
        }

        private fun generateRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        private fun toPem(key: java.security.Key, type: String): String = "-----BEGIN $type-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.encoded) +
            "\n-----END $type-----\n"
    }
}
