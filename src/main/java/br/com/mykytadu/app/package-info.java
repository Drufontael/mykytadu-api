/**
 * Application bootstrap, configuration and module composition.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Application",
        allowedDependencies = {"api", "identity", "translation", "shared"}
)
package br.com.mykytadu.app;
