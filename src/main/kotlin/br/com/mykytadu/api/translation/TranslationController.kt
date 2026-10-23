package br.com.mykytadu.api.translation

import br.com.mykytadu.api.error.ApiProblemException
import br.com.mykytadu.api.error.ApiProblemFactory
import br.com.mykytadu.api.error.ProblemCode
import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.api.TranslationService
import br.com.mykytadu.translation.api.TranslationSourceInfo
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("!test")
@RequestMapping("/api/v1/translations")
@PreAuthorize("isAuthenticated()")
internal class TranslationController(
    private val translationService: TranslationService,
    private val problemFactory: ApiProblemFactory,
) {

    @PostMapping
    fun translate(
        @Valid @RequestBody request: TranslationRequestDto,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val sourceLang = Language.fromCode(request.sourceLanguage)
        val targetLang = Language.fromCode(request.targetLanguage)
        val contentType = ContentType.fromValue(request.contentType)

        val command = TranslateContentCommand(
            text = request.text,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            contentType = contentType,
            source = request.source?.let { TranslationSourceInfo(it.system, it.externalId) },
        )

        return when (val outcome = translationService.translate(command)) {
            is TranslationOutcome.Success -> {
                val body = TranslationResponseDto(
                    translation = outcome.result.translation,
                    sourceLanguage = outcome.result.sourceLanguage.code,
                    targetLanguage = outcome.result.targetLanguage.code,
                    contentType = outcome.result.contentType.value,
                    cached = outcome.result.cached,
                )
                ResponseEntity.ok(body)
            }

            is TranslationOutcome.InvalidContent -> {
                val problem = problemFactory.create(
                    status = HttpStatus.BAD_REQUEST,
                    code = ProblemCode.REQUEST_VALIDATION_FAILED,
                    request = servletRequest,
                    errors = listOf(mapOf("field" to outcome.field, "message" to outcome.message)),
                    detail = outcome.message,
                )
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
            }

            TranslationOutcome.Timeout ->
                fail(HttpStatus.GATEWAY_TIMEOUT, ProblemCode.TRANSLATION_PROVIDER_TIMEOUT)

            is TranslationOutcome.ProviderFailed ->
                fail(HttpStatus.BAD_GATEWAY, ProblemCode.TRANSLATION_PROVIDER_FAILED)
        }
    }

    private fun fail(status: HttpStatus, code: ProblemCode): Nothing = throw ApiProblemException(status, code)
}
