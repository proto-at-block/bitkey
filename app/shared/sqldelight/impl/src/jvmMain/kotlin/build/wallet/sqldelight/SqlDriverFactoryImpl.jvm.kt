package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import build.wallet.logging.*
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.databasesDir
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory
import java.util.Properties

/**
 * JVM implementation of the [SqlDriverFactory].
 */
actual class SqlDriverFactoryImpl actual constructor(
  platformContext: PlatformContext,
  private val fileDirectoryProvider: FileDirectoryProvider,
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  uuidGenerator: UuidGenerator,
  appVariant: AppVariant,
  databaseIntegrityChecker: DatabaseIntegrityChecker,
) : SqlDriverFactory {
  actual override suspend fun createDriver(
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
