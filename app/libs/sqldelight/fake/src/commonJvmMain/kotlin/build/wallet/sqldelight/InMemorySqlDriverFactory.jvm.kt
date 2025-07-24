package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * In-memory JVM/Android implementation of the [SqlDriverFactory], uses in-memory Jdbc.
 */
actual class InMemorySqlDriverFactory : SqlDriverFactory {
  actual var sqlDriver: SqlDriver? = null

  actual override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    val driver = JdbcSqliteDriver(
      JdbcSqliteDriver.IN_MEMORY,
      Properties().apply { put("foreign_keys", "true") }
    )
    dataBaseSchema.create(driver)
    sqlDriver = driver
    return driver
  }
}
