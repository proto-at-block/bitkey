package build.wallet.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.random.UuidGenerator
import build.wallet.store.EncryptedKeyValueStoreFactory

expect class SqlDriverFactoryImpl(
  platformContext: PlatformContext,
  fileDirectoryProvider: FileDirectoryProvider,
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  uuidGenerator: UuidGenerator,
  appVariant: AppVariant,
  databaseIntegrityChecker: DatabaseIntegrityChecker,
) : SqlDriverFactory {
  override suspend fun createDriver(
    dataBaseName: String,
    dataBaseSchema: SqlSchema<QueryResult.Value<Unit>>,
  ): SqlDriver
}

@Suppress("ForbiddenMethodCall")
internal suspend fun loadDbKey(
  encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
  databaseIntegrityChecker: DatabaseIntegrityChecker,
  uuidGenerator: UuidGenerator,
): String {
  val suspendSettings = encryptedKeyValueStoreFactory
    // Changing these values is a breaking change
    // These should only be changed with a migration plan otherwise data will be lost
    .getOrCreate(storeName = "SqlCipherStore")

  val databaseEncryptionKey = suspendSettings.getStringOrNull("db-key")

  // Ensure the database file and encryption keys are in an expected state.
  val isValid =
    databaseIntegrityChecker.purgeDatabaseStateIfInvalid(databaseEncryptionKey = databaseEncryptionKey)

  return if (isValid && databaseEncryptionKey != null) {
    // If we already have a db key, we're guaranteed to be in a valid state, just return the
    // key
    databaseEncryptionKey
  } else {
    // Otherwise, create a new db key.
    val newKey = uuidGenerator.random()
    suspendSettings.putString("db-key", newKey)
    newKey
  }
}

internal class DbNotEncryptedException(message: String?) : Exception(message)
