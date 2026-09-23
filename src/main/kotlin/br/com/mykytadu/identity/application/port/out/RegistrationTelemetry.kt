package br.com.mykytadu.identity.application.port.out

interface RegistrationTelemetry {

    fun initialDeliveryUnavailable()

    fun resendDeliveryUnavailable()

    fun resendProcessingFailed()
}
