package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.inMemoryDriver

/**
 * In-memory iOS implementation of the [SqlDriverFactory], uses native driver.
 */
actual class InMemorySqlDriverFactory : SqlDriverFactory {
  actual var sqlDriver: SqlDriver? = null

  actual override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    return inMemoryDriver(dataBaseSchema).also {
      sqlDriver = it
    }
  }
}
