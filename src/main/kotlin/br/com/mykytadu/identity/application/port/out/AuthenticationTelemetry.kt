package br.com.mykytadu.identity.application.port.out

interface AuthenticationTelemetry {

    fun accepted()

    fun invalidCredentials()

    fun emailVerificationRequired()

    fun rateLimited()
}
