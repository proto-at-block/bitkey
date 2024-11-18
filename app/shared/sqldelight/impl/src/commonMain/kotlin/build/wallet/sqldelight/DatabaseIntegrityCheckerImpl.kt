package build.wallet.sqldelight

import build.wallet.platform.data.FileDirectoryProvider

expect class DatabaseIntegrityCheckerImpl(
  fileDirectoryProvider: FileDirectoryProvider,
) : DatabaseIntegrityChecker {
  override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?): Boolean
}
