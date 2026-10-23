package br.com.mykytadu.translation.domain.exception

open class TranslationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

open class TranslationProviderException(message: String, cause: Throwable? = null) :
    TranslationException(message, cause)

class TranslationProviderTimeoutException(
    message: String = "Translation provider timed out",
    cause: Throwable? = null,
) : TranslationProviderException(message, cause)

class TranslationProviderUnavailableException(
    message: String = "Translation provider is unavailable",
    cause: Throwable? = null,
) : TranslationProviderException(message, cause)

class TranslationProviderQuotaExceededException(
    message: String = "Translation provider quota exceeded",
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : TranslationProviderException(message, cause)

class TranslationProviderAuthenticationException(
    message: String = "Translation provider authentication failed",
    cause: Throwable? = null,
) : TranslationProviderException(message, cause)
