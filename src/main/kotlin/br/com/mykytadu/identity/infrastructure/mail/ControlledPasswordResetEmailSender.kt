package br.com.mykytadu.identity.infrastructure.mail

import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.PasswordResetEmailSender
import org.slf4j.LoggerFactory

internal class ControlledPasswordResetEmailSender(private val mode: EmailDeliveryMode) : PasswordResetEmailSender {

    private val logger = LoggerFactory.getLogger(ControlledPasswordResetEmailSender::class.java)

    override fun send(recipient: String, actionToken: String): DeliveryStatus = when (mode) {
        EmailDeliveryMode.ACCEPT -> {
            logger.info(
                "[LOCAL-DEV] Token de recuperação de senha gerado para <{}>: {}",
                recipient,
                actionToken,
            )
            logger.info(
                "[LOCAL-DEV] Para resetar via POST: http://localhost:8081/api/v1/auth/password/reset " +
                    "com body: {{\"actionToken\":\"{}\",\"newPassword\":\"...\"}}",
                actionToken,
            )
            DeliveryStatus.ACCEPTED
        }

        EmailDeliveryMode.UNAVAILABLE -> DeliveryStatus.UNAVAILABLE
    }
}
