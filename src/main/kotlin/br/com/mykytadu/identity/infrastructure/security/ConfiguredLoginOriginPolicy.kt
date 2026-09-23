package br.com.mykytadu.identity.infrastructure.security

import br.com.mykytadu.identity.application.port.out.LoginOriginPolicy

internal class ConfiguredLoginOriginPolicy(private val allowedOrigins: Set<String>) : LoginOriginPolicy {
    override fun isAllowed(origin: String?): Boolean = origin != null && origin in allowedOrigins
}
