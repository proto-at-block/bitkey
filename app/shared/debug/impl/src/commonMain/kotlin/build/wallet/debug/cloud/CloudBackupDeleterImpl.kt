package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(AppScope::class)
class CloudBackupDeleterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : CloudBackupDeleter {
  override suspend fun delete() {
    check(appVariant != Customer) {
      "Not allowed to clear cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          cloudBackupRepository.clear(
            it,
            clearRemoteOnly = true
          ).logFailure { "Error deleting cloud backup" }
        }
      }
      .onFailure { error ->
        logError { "Failed to find cloud account for deleting backup: $error" }
      }
    cloudStoreAccountRepository.clear()
      .logFailure { "Failed to clear cloud storage account" }
  }
}
