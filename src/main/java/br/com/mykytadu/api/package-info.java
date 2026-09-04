/**
 * Shared HTTP boundary: contracts, validation, security filters and errors.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "API",
        allowedDependencies = {"identity", "translation", "shared"}
)
package br.com.mykytadu.api;
