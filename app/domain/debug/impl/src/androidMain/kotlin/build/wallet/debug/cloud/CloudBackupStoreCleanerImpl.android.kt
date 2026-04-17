package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * Android debug deletion clears Google Drive app-data keys for the active cloud account.
 */
@BitkeyInject(AppScope::class)
class CloudBackupStoreCleanerImpl(
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupStoreCleaner {
  /**
   * Android backups live in Google Drive app-data; only known backup keys are removed.
   */
  @Suppress("UNUSED_PARAMETER")
  override suspend fun deleteBackupsIn(
    type: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val keys = cloudKeyValueStore.keys(cloudStoreAccount).bind()

      keys
        .filter(::isCloudBackupKey)
        .forEach { key ->
          cloudKeyValueStore.removeString(cloudStoreAccount, key)
            .bind()
        }
    }

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)
}
