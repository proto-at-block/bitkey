package build.wallet.keybox

import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.CloudStoreServiceProvider
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class CloudBackupDeleterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : CloudBackupDeleter {
  override suspend fun delete(cloudStoreServiceProvider: CloudStoreServiceProvider) {
    check(appVariant != Customer) {
      "Not allowed to clear cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudStoreServiceProvider)
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          cloudBackupRepository.clear(it).logFailure { "Error deleting cloud backup" }
        }
      }
      .onFailure { error ->
        log(LogLevel.Error) { "Failed to find cloud account for deleting backup: $error" }
      }
    cloudStoreAccountRepository.clear()
      .logFailure { "Failed to clear cloud storage account" }
  }
}
