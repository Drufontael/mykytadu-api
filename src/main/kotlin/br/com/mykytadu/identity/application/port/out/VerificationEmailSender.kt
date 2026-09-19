package br.com.mykytadu.identity.application.port.out

interface VerificationEmailSender {

    fun send(recipient: String, actionToken: String): DeliveryStatus
}

enum class DeliveryStatus {
    ACCEPTED,
    UNAVAILABLE,
}
