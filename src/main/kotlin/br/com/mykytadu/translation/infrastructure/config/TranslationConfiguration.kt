package br.com.mykytadu.translation.infrastructure.config

import br.com.mykytadu.translation.application.port.TranslationCache
import br.com.mykytadu.translation.application.port.TranslationIdGenerator
import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.infrastructure.client.LibreTranslateProviderAdapter
import br.com.mykytadu.translation.infrastructure.client.TranslationProviderProperties
import br.com.mykytadu.translation.infrastructure.fake.FakeTranslationProvider
import br.com.mykytadu.translation.infrastructure.fake.InMemoryTranslationCache
import br.com.mykytadu.translation.infrastructure.persistence.SecureTranslationIdGenerator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration
@EnableConfigurationProperties(TranslationProviderProperties::class)
class TranslationConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "mykytadu.translation.provider",
        name = ["mode"],
        havingValue = "libretranslate",
        matchIfMissing = true,
    )
    fun libreTranslateProvider(
        properties: TranslationProviderProperties,
        restClientBuilderProvider: ObjectProvider<RestClient.Builder>,
    ): TranslationProvider {
        val builder = restClientBuilderProvider.ifAvailable ?: RestClient.builder()
        return LibreTranslateProviderAdapter.createDefault(properties, builder)
    }

    @Bean
    @ConditionalOnMissingBean(TranslationProvider::class)
    fun fakeTranslationProvider(): TranslationProvider = FakeTranslationProvider()

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun translationClock(): Clock = Clock.systemUTC()

    @Bean
    @ConditionalOnMissingBean(TranslationIdGenerator::class)
    fun translationIdGenerator(clock: Clock): TranslationIdGenerator = SecureTranslationIdGenerator(clock)

    @Bean
    @ConditionalOnMissingBean(TranslationCache::class)
    fun inMemoryTranslationCache(): TranslationCache = InMemoryTranslationCache()
}
