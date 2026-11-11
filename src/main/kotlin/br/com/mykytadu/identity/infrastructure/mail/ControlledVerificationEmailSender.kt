package br.com.mykytadu.identity.infrastructure.mail

import br.com.mykytadu.identity.application.port.out.DeliveryStatus
import br.com.mykytadu.identity.application.port.out.VerificationEmailSender
import org.slf4j.LoggerFactory

internal class ControlledVerificationEmailSender(private val mode: EmailDeliveryMode) : VerificationEmailSender {

    private val logger = LoggerFactory.getLogger(ControlledVerificationEmailSender::class.java)

    override fun send(recipient: String, actionToken: String): DeliveryStatus = when (mode) {
        EmailDeliveryMode.ACCEPT -> {
            logger.info(
                "[LOCAL-DEV] Token de verificação de e-mail gerado para <{}>: {}",
                recipient,
                actionToken,
            )
            logger.info(
                "[LOCAL-DEV] Para verificar via POST: http://localhost:8081/api/v1/auth/verify-email " +
                    "com body: {{\"actionToken\":\"{}\"}}",
                actionToken,
            )
            DeliveryStatus.ACCEPTED
        }

        EmailDeliveryMode.UNAVAILABLE -> DeliveryStatus.UNAVAILABLE
    }
}

enum class EmailDeliveryMode {
    ACCEPT,
    UNAVAILABLE,
}
