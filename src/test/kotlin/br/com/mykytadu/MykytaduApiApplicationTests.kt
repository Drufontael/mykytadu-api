package br.com.mykytadu

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class MykytaduApiApplicationTests {

    @Test
    fun contextLoads() = Unit
}
