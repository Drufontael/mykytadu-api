package br.com.mykytadu.translation.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mykytadu.translation.quota")
data class TranslationQuotaProperties(val dailyCharactersLimit: Long = 50000, val dailyRequestsLimit: Int = 500)
