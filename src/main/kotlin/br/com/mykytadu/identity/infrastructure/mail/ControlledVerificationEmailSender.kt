package br.com.mykytadu.identity.infrastructure.mail

import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.VerificationEmailSender

internal class ControlledVerificationEmailSender(private val mode: EmailDeliveryMode) : VerificationEmailSender {

    override fun send(recipient: String, actionToken: String): DeliveryStatus = when (mode) {
        EmailDeliveryMode.ACCEPT -> DeliveryStatus.ACCEPTED
        EmailDeliveryMode.UNAVAILABLE -> DeliveryStatus.UNAVAILABLE
    }
}

enum class EmailDeliveryMode {
    ACCEPT,
    UNAVAILABLE,
}
