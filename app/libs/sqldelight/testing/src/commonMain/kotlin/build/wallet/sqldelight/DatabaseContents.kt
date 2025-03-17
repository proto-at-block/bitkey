package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import build.wallet.sqldelight.DatabaseContents.TableContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Describes contents of all tables in a database.
 *
 * [SqlDriver.databaseContents] is the main way to read database contents.
 *
 * Used for debugging and testing purposes.
 */
data class DatabaseContents(
  val tables: List<TableContents>,
) {
  /**
   * Describes contents of a table.
   *
   * @property columnNames - list of all column names for the table.
   * @property rowValues - map of row values where key is the name of a column, and the value is a
   * list of values in all rows for that column.
   */
  data class TableContents(
    val tableName: String,
    val columnNames: List<String>,
    val rowValues: Map<String, List<String>>,
  )
}

/**
 * Reads all column names and row values from all tables of this database and puts them
 * into [DatabaseContents] structure.
 */
suspend fun SqlDriver.databaseContents(): DatabaseContents {
  val tables =
    tableNames().map { tableName ->
      tableContents(tableName)
    }
  return DatabaseContents(tables)
}

suspend fun SqlDriver.tableNames(): List<String> {
  return withContext(Dispatchers.IO) {
    executeQuery(
      identifier = null,
      sql = "SELECT name FROM sqlite_master WHERE type='table'",
      mapper = {
        QueryResult.Value(
          buildList {
            while (it.next().value) {
              add(requireNotNull(it.getString(0)!!))
            }
          }
        )
      },
      parameters = 0
    ).value
  }
}

/**
 * Get [TableContents] with column names and row values from the given table.
 */
suspend fun SqlDriver.tableContents(tableName: String): TableContents {
  val columnNames = columnNames(tableName)

  val rowValues: Map<String, List<String>> =
    columnNames.associateWith { columnName ->
      // Get all values for the given column.
      executeQuery(
        identifier = null,
        sql = "SELECT $columnName FROM $tableName",
        mapper = {
          QueryResult.Value(
            buildList {
              while (it.next().value) {
                add(it.getString(0).orEmpty())
              }
            }
          )
        },
        parameters = 0
      ).value
    }

  return TableContents(
    tableName = tableName,
    columnNames = columnNames,
    rowValues = rowValues
  )
}

/**
 * Get list of column names for the given table.
 */
private suspend fun SqlDriver.columnNames(tableName: String): List<String> {
  return withContext(Dispatchers.IO) {
    executeQuery(
      identifier = null,
      sql = "PRAGMA table_info($tableName)",
      mapper = {
        QueryResult.Value(
          buildList {
            while (it.next().value) {
              val columnName = requireNotNull(it.getString(1))
              add(columnName)
            }
          }
        )
      },
      parameters = 0
    ).value
  }
}
