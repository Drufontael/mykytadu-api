package br.com.mykytadu.translation.application.port

import java.util.UUID

fun interface TranslationIdGenerator {
    fun nextId(): UUID
}
