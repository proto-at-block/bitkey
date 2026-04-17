package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * JVM debug deletion removes only known backup keys from the fake cloud backup store.
 */
@BitkeyInject(AppScope::class)
class CloudBackupStoreCleanerImpl(
  private val cloudBackupStore: CloudBackupStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupStoreCleaner {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun deleteBackupsIn(
    type: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val keys = cloudBackupStore.keys(cloudStoreAccount).bind()

      keys
        .filter(::isCloudBackupKey)
        .forEach { key ->
          cloudBackupStore.remove(cloudStoreAccount, key)
            .bind()
        }
    }

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)
}
