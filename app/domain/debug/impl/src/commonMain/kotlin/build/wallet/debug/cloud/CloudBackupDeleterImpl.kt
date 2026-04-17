package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackupService
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
  private val cloudBackupService: CloudBackupService,
  private val cloudBackupStoreCleaner: CloudBackupStoreCleaner,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : CloudBackupDeleter {
  override suspend fun delete(accountId: AccountId?) {
    check(appVariant != Customer) {
      "Not allowed to clear cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          cloudBackupService.clear(
            accountId = accountId,
            cloudStoreAccount = it,
            // Keep local and remote cloud backup states aligned for debug deletion flows.
            clearRemoteOnly = false
          ).logFailure { "Error deleting cloud backup" }
        }
      }
      .onFailure { error ->
        logError { "Failed to find cloud account for deleting backup: $error" }
      }
    cloudStoreAccountRepository.clear()
      .logFailure { "Failed to clear cloud storage account" }
  }

  override suspend fun deleteAllBackups() {
    check(appVariant != Customer) {
      "Not allowed to clear cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          cloudBackupService.clearAll(
            cloudStoreAccount = it,
            // Keep local and remote cloud backup states aligned for debug deletion flows.
            clearRemoteOnly = false
          ).logFailure { "Error deleting cloud backup" }
        }
      }
      .onFailure { error ->
        logError { "Failed to find cloud account for deleting backup: $error" }
      }
    cloudStoreAccountRepository.clear()
      .logFailure { "Failed to clear cloud storage account" }
  }

  override suspend fun deleteBackupsIn(type: CloudBackupStoreType) {
    check(appVariant != Customer) {
      "Not allowed to clear cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          cloudBackupStoreCleaner.deleteBackupsIn(type, it)
            .logFailure { "Error deleting cloud backup" }
        }
      }
      .onFailure { error ->
        logError { "Failed to find cloud account for deleting backup: $error" }
      }
    cloudStoreAccountRepository.clear()
      .logFailure { "Failed to clear cloud storage account" }
  }
}
