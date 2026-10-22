package br.com.mykytadu.api.identity

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.identity.api.GetProfileQuery
import br.com.mykytadu.identity.api.IdentityProfile
import br.com.mykytadu.identity.api.ProfileOutcome
import br.com.mykytadu.identity.api.UpdateProfileCommand
import br.com.mykytadu.identity.api.UserProfileDto
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@RestController
@Profile("!test")
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('USER')")
internal class UserProfileController(private val profile: IdentityProfile, private val objectMapper: ObjectMapper) {

    @GetMapping
    fun getCurrentProfile(@AuthenticationPrincipal principal: Jwt): ResponseEntity<UserProfileResponse> {
        val userId = UUID.fromString(principal.subject)
        return when (val outcome = profile.getProfile(GetProfileQuery(userId))) {
            is ProfileOutcome.Success -> ResponseEntity.ok(outcome.profile.toResponse())

            ProfileOutcome.NotFound,
            ProfileOutcome.Inactive,
            -> fail(HttpStatus.UNAUTHORIZED, ProblemCode.AUTHENTICATION_REQUIRED)
        }
    }

    @PatchMapping
    fun updateCurrentProfile(
        @AuthenticationPrincipal principal: Jwt,
        @RequestBody body: String,
    ): ResponseEntity<UserProfileResponse> {
        val displayName = extractAndValidateDisplayName(body)
        val userId = UUID.fromString(principal.subject)
        return when (val outcome = profile.updateProfile(UpdateProfileCommand(userId, displayName))) {
            is ProfileOutcome.Success -> ResponseEntity.ok(outcome.profile.toResponse())

            ProfileOutcome.NotFound,
            ProfileOutcome.Inactive,
            -> fail(HttpStatus.UNAUTHORIZED, ProblemCode.AUTHENTICATION_REQUIRED)
        }
    }

    private fun extractAndValidateDisplayName(body: String): String? {
        val node = parseJsonTree(body)
        val properties = node.properties()
        if (properties.isEmpty() || properties.any { it.key != "displayName" }) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        val displayNode = node.get("displayName")
        return when {
            displayNode == null || displayNode.isNull -> null
            displayNode.isString -> validateDisplayNameText(displayNode.asString())
            else -> fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
    }

    private fun parseJsonTree(body: String): JsonNode {
        val node = try {
            objectMapper.readTree(body)
        } catch (_: Exception) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        if (!node.isObject) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        return node
    }

    private fun validateDisplayNameText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > DISPLAY_NAME_MAX_LENGTH) {
            fail(HttpStatus.BAD_REQUEST, ProblemCode.REQUEST_VALIDATION_FAILED)
        }
        return trimmed
    }

    private fun UserProfileDto.toResponse(): UserProfileResponse = UserProfileResponse(
        id = id,
        email = email,
        displayName = displayName,
        status = status,
        roles = roles,
        emailVerifiedAt = emailVerifiedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun fail(status: HttpStatus, code: ProblemCode): Nothing = throw ApiProblemException(status, code)

    companion object {
        private const val DISPLAY_NAME_MAX_LENGTH = 80
    }
}
