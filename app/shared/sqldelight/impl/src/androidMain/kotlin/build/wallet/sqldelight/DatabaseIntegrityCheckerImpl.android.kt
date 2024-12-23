package build.wallet.sqldelight

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class DatabaseIntegrityCheckerImpl : DatabaseIntegrityChecker {
  override suspend fun purgeDatabaseStateIfInvalid(databaseEncryptionKey: String?): Boolean {
    // no-op
    return true
  }
}
