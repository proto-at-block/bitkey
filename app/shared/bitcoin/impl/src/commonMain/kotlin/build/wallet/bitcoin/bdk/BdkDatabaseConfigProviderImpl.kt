package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkDatabaseConfig
import build.wallet.bdk.bindings.BdkSqliteDbConfiguration
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.databasesDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class BdkDatabaseConfigProviderImpl(
  private val fileDirectoryProvider: FileDirectoryProvider,
) : BdkDatabaseConfigProvider {
  override suspend fun sqliteConfig(identifier: String): BdkDatabaseConfig {
    return bdkDatabaseConfigOverride() ?: withContext(Dispatchers.IO) {
      val databaseFileName = "$identifier-bdk.db"
      val databasePath = fileDirectoryProvider.databasesDir().join(databaseFileName)
      BdkDatabaseConfig.Sqlite(
        config = BdkSqliteDbConfiguration(databasePath)
      )
    }
  }
}

/**
 * A workaround to override bdk database config in JVM tests.
 *
 * TODO(W-3639): implement a more elegant, dynamic approach to override config.
 */
expect fun bdkDatabaseConfigOverride(): BdkDatabaseConfig?
