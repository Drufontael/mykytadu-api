package br.com.mykytadu.translation.application

import br.com.mykytadu.translation.api.TranslateContentCommand
import br.com.mykytadu.translation.api.TranslatedContent
import br.com.mykytadu.translation.api.TranslationOutcome
import br.com.mykytadu.translation.api.TranslationService
import br.com.mykytadu.translation.application.port.ProviderTranslationCommand
import br.com.mykytadu.translation.application.port.TranslationCache
import br.com.mykytadu.translation.application.port.TranslationIdGenerator
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.application.port.TranslationTelemetry
import br.com.mykytadu.translation.application.port.TranslationUsageTracker
import br.com.mykytadu.translation.domain.exception.InvalidContentException
import br.com.mykytadu.translation.domain.exception.TranslationProviderException
import br.com.mykytadu.translation.domain.exception.TranslationProviderTimeoutException
import br.com.mykytadu.translation.domain.model.CachedTranslation
import br.com.mykytadu.translation.domain.model.ContentType
import br.com.mykytadu.translation.domain.model.Language
import br.com.mykytadu.translation.domain.service.ContentNormalizer
import br.com.mykytadu.translation.domain.service.NormalizedContent
import br.com.mykytadu.translation.domain.service.TranslationKeyGenerator
import br.com.mykytadu.translation.infrastructure.config.TranslationQuotaProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

@Service
class TranslateContentUseCase(
    private val provider: TranslationProvider,
    private val cache: TranslationCache,
    private val idGenerator: TranslationIdGenerator,
    private val usageTracker: TranslationUsageTracker,
    private val quotaProperties: TranslationQuotaProperties,
    private val telemetry: TranslationTelemetry,
    private val clock: Clock,
) : TranslationService {

    override fun translate(command: TranslateContentCommand): TranslationOutcome {
        val normalized = try {
            ContentNormalizer.normalize(command.text)
        } catch (ex: InvalidContentException) {
            return TranslationOutcome.InvalidContent(ex.message ?: "Invalid content")
        }

        return processTranslation(command, normalized)
    }

    @Suppress("ReturnCount")
    private fun processTranslation(
        command: TranslateContentCommand,
        normalized: NormalizedContent,
    ): TranslationOutcome {
        val sourceLang = Language.fromCode(command.sourceLanguage)
        val targetLang = Language.fromCode(command.targetLanguage)
        val contentType = ContentType.fromValue(command.contentType)

        val contentHash = TranslationKeyGenerator.generateKey(
            normalizedText = normalized.normalizedText.value,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            contentType = contentType,
        )

        val now = clock.instant()
        val cached = cache.findValidTranslation(contentHash, targetLang, now)
        if (cached != null) {
            telemetry.recordCacheHit()
            return TranslationOutcome.Success(
                TranslatedContent(
                    translation = cached.translation,
                    sourceLanguage = cached.sourceLanguage.code,
                    targetLanguage = cached.targetLanguage.code,
                    contentType = cached.contentType.value,
                    cached = true,
                ),
            )
        }

        telemetry.recordCacheMiss()

        if (command.userId != null) {
            val usageDate = now.atZone(ZoneOffset.UTC).toLocalDate()
            val currentUsage = usageTracker.getDailyCharactersCount(command.userId, usageDate)
            val requestedChars = normalized.normalizedText.value.length
            if (currentUsage + requestedChars > quotaProperties.dailyCharactersLimit) {
                val nextMidnightUtc = usageDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                val retryAfterSeconds = Duration.between(now, nextMidnightUtc).toSeconds().coerceAtLeast(1L)
                logger.warn(
                    "Translation quota exceeded for user={}, current={}, requested={}, limit={}",
                    command.userId,
                    currentUsage,
                    requestedChars,
                    quotaProperties.dailyCharactersLimit,
                )
                return TranslationOutcome.QuotaExceeded(retryAfterSeconds)
            }
        }

        val providerCommand = ProviderTranslationCommand(
            text = normalized.normalizedText.value,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            contentType = contentType,
            format = normalized.format,
        )

        return executeProviderTranslation(
            providerCommand = providerCommand,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            contentType = contentType,
            contentHash = contentHash,
            userId = command.userId,
            characterCount = normalized.normalizedText.value.length,
        )
    }

    @Suppress("LongParameterList")
    private fun executeProviderTranslation(
        providerCommand: ProviderTranslationCommand,
        sourceLanguage: Language,
        targetLanguage: Language,
        contentType: ContentType,
        contentHash: String,
        userId: UUID?,
        characterCount: Int,
    ): TranslationOutcome {
        val startNanos = System.nanoTime()
        return try {
            val providerResult = provider.translate(providerCommand)
            val duration = Duration.ofNanos(System.nanoTime() - startNanos)
            telemetry.recordProviderCall(duration, success = true, provider = providerResult.provider)
            telemetry.recordCharactersTranslated(characterCount)
            val resolvedSource = providerResult.detectedSourceLanguage ?: sourceLanguage
            val now = clock.instant()

            val cacheEntry = CachedTranslation(
                id = idGenerator.nextId(),
                contentHash = contentHash,
                sourceLanguage = resolvedSource,
                targetLanguage = targetLanguage,
                contentType = contentType,
                translation = providerResult.translatedText,
                provider = providerResult.provider,
                modelVersion = providerResult.modelVersion,
                createdAt = now,
                expiresAt = now.plus(RETENTION_DURATION),
            )
            cache.save(cacheEntry)

            if (userId != null) {
                val usageDate = now.atZone(ZoneOffset.UTC).toLocalDate()
                usageTracker.recordUsage(userId, usageDate, characterCount)
            }

            val response = TranslatedContent(
                translation = providerResult.translatedText,
                sourceLanguage = resolvedSource.code,
                targetLanguage = targetLanguage.code,
                contentType = contentType.value,
                cached = false,
            )
            TranslationOutcome.Success(response)
        } catch (ex: TranslationProviderTimeoutException) {
            val duration = Duration.ofNanos(System.nanoTime() - startNanos)
            telemetry.recordProviderCall(duration, success = false, provider = "libretranslate")
            logger.warn("Translation timed out: {}", ex.message)
            TranslationOutcome.Timeout
        } catch (ex: TranslationProviderException) {
            val duration = Duration.ofNanos(System.nanoTime() - startNanos)
            telemetry.recordProviderCall(duration, success = false, provider = "libretranslate")
            logger.warn("Translation provider failed: {}", ex.message)
            TranslationOutcome.ProviderFailed(ex.message)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranslateContentUseCase::class.java)
        private val RETENTION_DURATION: Duration = Duration.ofDays(30)
    }
}
