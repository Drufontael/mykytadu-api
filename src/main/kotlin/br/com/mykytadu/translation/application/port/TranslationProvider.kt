package br.com.mykytadu.translation.application.port

interface TranslationProvider {
    fun translate(command: ProviderTranslationCommand): ProviderTranslationResult
}
