package br.com.mykytadu.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

class MigrationConventionTests {

    @Test
    fun `migration names are versioned ordered unique and owned by a module`() {
        val names = migrationResources()
            .mapNotNull { it.filename }
            .sortedBy { versionOf(it) }

        assertThat(names).isNotEmpty()
        assertThat(names).allMatch { VERSIONED_NAME.matches(it) }
        assertThat(names.map(::versionOf)).doesNotHaveDuplicates().isSorted()
        assertThat(names).contains(BOOTSTRAP_MIGRATION)

        names.filterNot { it == BOOTSTRAP_MIGRATION }
            .map { it.substringAfter('-').removeSuffix(".sql") }
            .forEach { description ->
                assertThat(OWNED_DESCRIPTION.matches(description))
                    .withFailMessage("Migration sem owner de módulo: %s", description)
                    .isTrue()
            }
    }

    private fun migrationResources() = PathMatchingResourcePatternResolver()
        .getResources("classpath*:db/migration/*.sql")

    private fun versionOf(name: String): Long = VERSIONED_NAME
        .matchEntire(name)?.groupValues?.get(1)?.toLong()
        ?: error("Nome de migration inválido: $name")

    companion object {

        private const val BOOTSTRAP_MIGRATION =
            "V20260904184904-create_identity_and_translation_schemas.sql"
        private val VERSIONED_NAME =
            Regex("^V(\\d{14})-([a-z0-9]+(?:_[a-z0-9]+)*)\\.sql$")
        private val OWNED_DESCRIPTION =
            Regex("^(identity|translation|platform)_[a-z0-9]+(?:_[a-z0-9]+)*$")
    }
}
