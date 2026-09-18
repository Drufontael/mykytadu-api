package br.com.mykytadu.integration

import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Technical-only helpers for integration tests backed by PostgreSQL.
 *
 * Every integration test class must call [newContainer] independently. The
 * fixture deliberately does not expose domain inserts or a shared container.
 */
object PostgreSqlIntegrationFixture {

    const val IMAGE = "postgres:18.6-trixie"

    fun newContainer(): PostgreSQLContainer = PostgreSQLContainer(IMAGE)

    fun currentDatabase(jdbcTemplate: JdbcTemplate): String? =
        jdbcTemplate.queryForObject("SELECT current_database()", String::class.java)

    fun successfulMigrationVersions(jdbcTemplate: JdbcTemplate): Set<String> = jdbcTemplate.queryForList(
        "SELECT version FROM flyway_schema_history WHERE success",
        String::class.java,
    ).filterNotNull().toSet()

    fun schemas(jdbcTemplate: JdbcTemplate, names: Collection<String>): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT schema_name
            FROM information_schema.schemata
            WHERE schema_name IN (${names.joinToString { "?" }})
        """.trimIndent(),
        String::class.java,
        *names.toTypedArray(),
    ).filterNotNull().toSet()

    fun tables(jdbcTemplate: JdbcTemplate, schema: String): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = ?
              AND table_type = 'BASE TABLE'
        """.trimIndent(),
        String::class.java,
        schema,
    ).filterNotNull().toSet()

    fun columns(jdbcTemplate: JdbcTemplate, schema: String, table: String): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = ?
        """.trimIndent(),
        String::class.java,
        schema,
        table,
    ).filterNotNull().toSet()

    fun foreignKeyTables(jdbcTemplate: JdbcTemplate, schema: String): Set<String> = jdbcTemplate.queryForList(
        """
            SELECT table_name
            FROM information_schema.table_constraints
            WHERE constraint_schema = ?
              AND constraint_type = 'FOREIGN KEY'
        """.trimIndent(),
        String::class.java,
        schema,
    ).filterNotNull().toSet()

    fun businessTablesInPublicSchema(jdbcTemplate: JdbcTemplate): Int = jdbcTemplate.queryForObject(
        """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name <> 'flyway_schema_history'
        """.trimIndent(),
        Int::class.java,
    ) ?: 0
}
