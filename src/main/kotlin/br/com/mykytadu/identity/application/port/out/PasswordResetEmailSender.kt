package br.com.mykytadu.identity.application.port.out

interface PasswordResetEmailSender {

    fun send(recipient: String, actionToken: String): DeliveryStatus
}
