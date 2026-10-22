package br.com.mykytadu.identity.application.port.out

interface PasswordRecoveryTelemetry {

    fun forgotPasswordAccepted()

    fun forgotPasswordRateLimited()

    fun forgotPasswordDeliveryUnavailable()

    fun forgotPasswordProcessingFailed()

    fun passwordResetCompleted()

    fun passwordResetInvalidToken()

    fun passwordResetProcessingFailed()
}
