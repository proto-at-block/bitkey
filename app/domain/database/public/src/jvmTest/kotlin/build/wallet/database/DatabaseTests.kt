package build.wallet.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.use
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.sqldelight.DbSpecDsl
import build.wallet.sqldelight.databaseContents
import io.kotest.assertions.failure
import io.kotest.assertions.withClue
import io.kotest.core.test.TestScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Creates a new database driver by copying the schema file to a temp file.
 *
 * This temp file ensures that tests do not share databases to reduce
 * test flakiness.
 */
private suspend fun createDatabaseAtVersion(version: Long): SqlDriver {
  val tempDatabase = withContext(Dispatchers.IO) {
    Files.createTempFile("temp-db", ".db").toFile().also { temp ->
      val schemaResource = File(DbSpecDsl::class.java.classLoader.getResource("databases/$version.db")!!.toURI())
      Files.copy(schemaResource.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }
  return JdbcSqliteDriver(
    "jdbc:sqlite:${tempDatabase.absolutePath}"
  )
}

/**
 * Migrate from version 1 to a specified version, installing fixtures for
 * each version.
 *
 * This will also assert that all tables are populated by fixtures for
 * each version. This is necessary because SQLite allows some table
 * alterations on empty tables that should not be allowed in our migrations.
 */
@Suppress("ThrowsCount")
private suspend fun DbSpecDsl.migrateAndInstallFixtures(version: Long) {
  (1..version).forEach { targetVersion ->
    withClue("Migration to database version <$targetVersion>") {
      runCatching { BitkeyDatabase.Schema.migrate(driver, targetVersion - 1L, targetVersion).await() }
        .onFailure { throw failure("SQLDelight Migration Failed: ${it.message}", it) }

      this::class.java.classLoader.getResource("fixtures/$targetVersion.sql")
        ?.readText()
        ?.let { text ->
          text.lineSequence()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
        }
        ?.split(';')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.map { statement ->
          runCatching { driver.execute(null, statement, 0).await() }
            .getOrElse { throw failure("Error while executing fixture query: $statement", it) }
        }

      val tableSizes = driver.databaseContents().tables.map { table ->
        table.tableName to table.rowValues.values.first().size
      }

      val emptyTables = tableSizes
        .filter { (_, size) -> size == 0 }
        .map { (table, _) -> table }

      if (emptyTables.isNotEmpty()) {
        throw failure(
          """
                        Database fixtures missing for tables [${emptyTables.joinToString()}]. 
                        Populate the table with at least one row in the fixtures file (fixtures/$targetVersion.sql)
          """.trimIndent()
        )
      }

      // Ensure FK references are intact post migrations.
      driver.execute(identifier = null, sql = "PRAGMA foreign_keys=ON", parameters = 0)
    }
  }
}

/**
 * Create and use an empty database using the schema at the specified [version].
 *
 * This can be used to test migrations by starting with an old database
 * schema before running tests.
 */
@Suppress("UnusedReceiverParameter") // Used to limit call scope to tests.
suspend fun TestScope.usingDatabase(version: Long, test: suspend DbSpecDsl.() -> Unit) {
  createDatabaseAtVersion(version).use { driver ->
    val dsl = DbSpecDsl(driver)
    withClue("Using Database Version: <$version>") {
      test(dsl)
    }
  }
}

/**
 * Create and use a database at the specified version, with test data.
 *
 * Creates the initial database and runs all migrations up to the
 * specified [version], as well as creating database fixtures to
 * populate the database based on the SQL files in:
 *     src/commonMain/sqldelight/fixtures/
 *
 * This can be used for simple migration tests using the existing
 * database fixtures:
 *
 * ```kotlin
 *     test("Column Was added with default value") {
 *         usingDatabaseWithFixtures(1) {
 *             table("exampleTable") {
 *                 // New column added from migration
 *                 columnNames.shouldNotContain("newColumn")
 *
 *                 rowAt(0) {
 *                     valueShouldBe("id", "my-fixture-id")
 *                     valueShouldBe("newColumn", "myDefaultValue")
 *                 }
 *             }
 *         }
 *     }
 * ```
 */
@Suppress("UnusedReceiverParameter") // Used to limit call scope to tests.
suspend fun TestScope.usingDatabaseWithFixtures(version: Long, test: suspend DbSpecDsl.() -> Unit) {
  createDatabaseAtVersion(1).use { driver ->
    val dsl = DbSpecDsl(driver)
    dsl.migrateAndInstallFixtures(version)
    withClue("Using Database w/Fixtures Version: <$version>") {
      test(dsl)
    }
  }
}
