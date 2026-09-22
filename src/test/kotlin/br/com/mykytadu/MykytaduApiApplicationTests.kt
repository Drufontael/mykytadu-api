package br.com.mykytadu

import br.com.mykytadu.identity.api.EmailVerificationOutcome
import br.com.mykytadu.identity.api.IdentityRegistration
import br.com.mykytadu.identity.api.RegisterAccountCommand
import br.com.mykytadu.identity.api.RegistrationOutcome
import br.com.mykytadu.identity.api.ResendVerificationCommand
import br.com.mykytadu.identity.api.ResendVerificationOutcome
import br.com.mykytadu.identity.api.VerifyEmailCommand
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(MykytaduApiApplicationTests.TestBeans::class)
class MykytaduApiApplicationTests {

    @Test
    fun contextLoads() = Unit

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {
        @Bean
        fun identityRegistration(): IdentityRegistration = object : IdentityRegistration {
            override fun register(command: RegisterAccountCommand): RegistrationOutcome =
                error("Identity registration is not available in the isolated context test")

            override fun resendVerification(command: ResendVerificationCommand): ResendVerificationOutcome =
                error("Identity registration is not available in the isolated context test")

            override fun verifyEmail(command: VerifyEmailCommand): EmailVerificationOutcome =
                error("Identity registration is not available in the isolated context test")
        }
    }
}
