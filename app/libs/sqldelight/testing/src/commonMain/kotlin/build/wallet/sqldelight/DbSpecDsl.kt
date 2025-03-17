package build.wallet.sqldelight

import app.cash.sqldelight.db.SqlDriver
import build.wallet.sqldelight.DatabaseContents.TableContents
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Test scope DSL for working with databases.
 *
 * This adds convenience methods for working with databases in tests
 * as well as improving error messages for assertions.
 */
class DbSpecDsl(
  val driver: SqlDriver,
) {
  /**
   * Select and use a table for testing.
   */
  suspend fun table(
    name: String,
    test: suspend TableContents.() -> Unit,
  ) {
    val table = withClue("Loading Table <$name>") {
      driver.databaseContents().tables.find { it.tableName == name }.shouldNotBeNull()
    }
    withClue("Using Table: <${table.tableName}>") {
      test(table)
    }
  }

  /**
   * Assert that a table does not exist in the database.
   */
  suspend fun tableShouldNotExist(name: String) {
    withClue("Table should not exist: <$name>") {
      driver.databaseContents().tables.find { it.tableName == name }.shouldBe(null)
    }
  }

  /**
   * Select a single row from a table by the row's index for testing.
   */
  suspend fun tableAtRow(
    table: String,
    row: Int,
    test: suspend RowScope.() -> Unit,
  ) {
    table(table) {
      rowAt(row, test)
    }
  }

  /**
   * Use a row at a specified index for testing.
   */
  suspend fun TableContents.rowAt(
    row: Int,
    test: suspend RowScope.() -> Unit,
  ) {
    withClue("At Row: <$row>") {
      test(RowScope(this, row))
    }
  }

  /**
   * Function scope for a specified table row.
   */
  class RowScope(private val table: TableContents, private val row: Int) {
    /**
     * Assert that the value of the row's column matches [value].
     */
    fun valueShouldBe(
      name: String,
      value: String,
    ) {
      withClue("Column: <$name>") {
        table.rowValues[name]?.get(row).shouldBe(value)
      }
    }
  }
}
