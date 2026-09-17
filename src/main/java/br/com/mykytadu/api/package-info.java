/**
 * Shared HTTP boundary: contracts, validation, security filters and errors.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "API",
        allowedDependencies = {"identity :: api", "translation :: api", "shared"}
)
package br.com.mykytadu.api;
