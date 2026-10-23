package br.com.mykytadu.translation.infrastructure.config

import br.com.mykytadu.translation.application.port.TranslationProvider
import br.com.mykytadu.translation.infrastructure.client.LibreTranslateProviderAdapter
import br.com.mykytadu.translation.infrastructure.client.TranslationProviderProperties
import br.com.mykytadu.translation.infrastructure.fake.FakeTranslationProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

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
}
