package br.com.mykytadu.architecture

import br.com.mykytadu.MykytaduApiApplication
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ApplicationArchitectureTests {

    private val modules = ApplicationModules.of(MykytaduApiApplication::class.java)

    @Test
    fun `discovers the expected application modules`() {
        val discoveredModules = modules.stream()
            .map { it.identifier.toString() }
            .toList()
            .toSet()

        assertThat(discoveredModules)
            .containsExactlyInAnyOrder("app", "api", "identity", "shared", "translation")
    }

    @Test
    fun `exposes only the named public boundaries of domain modules`() {
        val identityInterfaces = modules.getModuleByName("identity")
            .orElseThrow()
            .namedInterfaces
            .map { it.name }
        val translationInterfaces = modules.getModuleByName("translation")
            .orElseThrow()
            .namedInterfaces
            .map { it.name }

        assertThat(identityInterfaces).contains("api")
        assertThat(translationInterfaces).contains("api")
    }

    @Test
    fun `accepts the current module arrangement`() {
        modules.verify()
    }

    @Test
    fun `rejects a dependency not declared by a module`() {
        val invalidModules = ApplicationModules.of(
            "mykytadu.fixture.invalid",
            ImportOption.OnlyIncludeTests(),
        )

        val violation = catchThrowable {
            invalidModules.verify()
        }

        assertThat(violation).hasMessageContaining("forbidden")
    }
}
