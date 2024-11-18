package build.wallet.sqldelight

import build.wallet.platform.data.FileDirectoryProvider

actual class DatabaseIntegrityCheckerImpl actual constructor(
  fileDirectoryProvider: FileDirectoryProvider,
) : DatabaseIntegrityChecker {
  actual override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?): Boolean {
    // no-op
    return true
  }
}
