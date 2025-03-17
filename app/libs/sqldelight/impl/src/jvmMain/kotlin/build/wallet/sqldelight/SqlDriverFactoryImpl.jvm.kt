package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.databasesDir
import java.util.*

/**
 * JVM implementation of the [SqlDriverFactory].
 */

@BitkeyInject(AppScope::class)
class SqlDriverFactoryImpl(
  private val fileDirectoryProvider: FileDirectoryProvider,
) : SqlDriverFactory {
  override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver {
    val databasePath = fileDirectoryProvider.databasesDir().join(dataBaseName)
    return JdbcSqliteDriver(
      url = "jdbc:sqlite:$databasePath",
      properties = Properties().apply { put("foreign_keys", "true") }
    ).also { driver ->
      @Suppress("TooGenericExceptionCaught")
      try {
        dataBaseSchema.create(driver)
      } catch (e: Exception) {
        if (e.message?.contains("already exists") == true) {
          logDebug { "Skipped running migrations" }
          return driver
        }
        throw e
      }
    }
  }
}
