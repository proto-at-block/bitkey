package build.wallet.sqldelight

import build.wallet.logging.*
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.databasesDir
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

actual class DatabaseIntegrityCheckerImpl actual constructor(
  private val fileDirectoryProvider: FileDirectoryProvider,
) : DatabaseIntegrityChecker {
  actual override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?): Boolean {
    val dbDirectory = fileDirectoryProvider.databasesDir()

    val nsFileManager = NSFileManager.defaultManager()

    if (nsFileManager.fileExistsAtPath(dbDirectory) && databaseEncryptionKey == null) {
      // If the database key is null AND the .db file has already been created, purge the database since
      // we have no way to decrypt it. This can happen if the .db file was backed up to e.g. iCloud
      // and then restored to another device, which would not have the encryption key.
      @OptIn(ExperimentalForeignApi::class)
      val success = nsFileManager.removeItemAtPath(path = dbDirectory, error = null)

      if (!success) {
        logError { "DatabaseIntegrityChecker failed to remove database directory." }
      }
      return false
    }

    return true
  }
}
