package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * An in-memory implementation of [SqlDriverFactory], helpful for unit testing.
 *
 * In tests, please us this implementation via [inMemoryDatabaseDriverExtension] from `:testing`
 * module.
 */
expect class InMemorySqlDriverFactory constructor() : SqlDriverFactory {
  /**
   * Returns latest [SqlDriver] instance created by [createDriver].
   */
  var sqlDriver: SqlDriver?
    private set

  override fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver
}
