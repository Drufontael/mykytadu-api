package br.com.mykytadu.architecture

import br.com.mykytadu.MykytaduApiApplication
import com.tngtech.archunit.core.importer.ImportOption
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.springframework.modulith.core.ApplicationModules

class ApplicationArchitectureTests {

    private val modules = ApplicationModules.of(MykytaduApiApplication::class.java)

    @Test
    fun `discovers the expected application modules`() {
        val discoveredModules = modules.stream()
            .map { it.identifier.toString() }
            .toList()
            .toSet()

        assertEquals(
            setOf("app", "api", "identity", "shared", "translation"),
            discoveredModules,
        )
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

        val violation = assertFails {
            invalidModules.verify()
        }

        assertContains(violation.message.orEmpty(), "forbidden")
    }
}
