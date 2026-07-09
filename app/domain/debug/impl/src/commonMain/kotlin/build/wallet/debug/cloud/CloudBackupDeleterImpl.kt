package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class CloudBackupDeleterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupService: CloudBackupService,
  private val cloudBackupStoreCleaner: CloudBackupStoreCleaner,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : CloudBackupDeleter {
  override suspend fun delete(accountId: AccountId?): Result<Unit, Error> =
    coroutineBinding {
      debugDeletionAllowed().bind()
      val cloudAccount = cloudAccount().bind()
      cloudBackupService.clear(
        accountId = accountId,
        cloudStoreAccount = cloudAccount,
        // Keep local and remote cloud backup states aligned for debug deletion flows.
        clearRemoteOnly = false
      )
        .mapError { Error("Error deleting cloud backup", it) }
        .bind()
      clearCloudAccount().bind()
    }

  override suspend fun deleteAllBackups(): Result<Unit, Error> =
    coroutineBinding {
      debugDeletionAllowed().bind()
      val cloudAccount = cloudAccount().bind()
      cloudBackupService.clearAll(
        cloudStoreAccount = cloudAccount,
        // Keep local and remote cloud backup states aligned for debug deletion flows.
        clearRemoteOnly = false
      )
        .mapError { Error("Error deleting cloud backup", it) }
        .bind()
      clearCloudAccount().bind()
    }

  override suspend fun deleteBackupsIn(type: CloudBackupStoreType): Result<Unit, Error> =
    coroutineBinding {
      debugDeletionAllowed().bind()
      val cloudAccount = cloudAccount().bind()
      cloudBackupStoreCleaner.deleteBackupsIn(type, cloudAccount)
        .mapError { Error("Error deleting cloud backup", it) }
        .bind()
      clearCloudAccount().bind()
    }

  private suspend fun cloudAccount(): Result<CloudStoreAccount, Error> =
    coroutineBinding {
      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { Error("Failed to find cloud account", it) }
        .bind()
      ensureNotNull(cloudAccount) {
        Error("No cloud account")
      }
    }

  private suspend fun clearCloudAccount(): Result<Unit, Error> =
    cloudStoreAccountRepository.clear()
      .mapError { Error("Failed to clear cloud storage account", it) }

  private fun debugDeletionAllowed(): Result<Unit, Error> =
    if (appVariant == Customer) {
      Err(
        Error("Not allowed to clear cloud backups in Customer builds.")
      )
    } else {
      Ok(Unit)
    }
}
