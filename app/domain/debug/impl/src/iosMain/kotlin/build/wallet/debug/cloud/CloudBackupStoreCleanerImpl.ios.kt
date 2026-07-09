package build.wallet.debug.cloud

import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.UbiquitousKeyValueStore
import build.wallet.cloud.store.cloudStoreAccountRouting
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * iOS debug deletion targets:
 * - [UbiquitousKvs]: clear only Ubiquitous KVS.
 * - [CloudKit]: clear only CloudKit records.
 */
@BitkeyInject(AppScope::class)
class CloudBackupStoreCleanerImpl(
  private val ubiquitousKeyValueStore: UbiquitousKeyValueStore,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val cloudBackupStore: CloudBackupStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
  private val accountConfigService: AccountConfigService,
) : CloudBackupStoreCleaner {
  override suspend fun deleteBackupsIn(
    type: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    if (usesFakeCloud(cloudStoreAccount)) {
      clearFakeCloudStore(cloudStoreAccount)
    } else {
      when (type) {
        UbiquitousKvs -> clearUbiquitousKvs(cloudStoreAccount)
        CloudKit -> clearCloudKitStore(cloudStoreAccount)
        else -> error("Unsupported CloudBackupStoreType on iOS: $type")
      }
    }

  private suspend fun clearFakeCloudStore(
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

  private suspend fun clearUbiquitousKvs(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val keys = ubiquitousKeyValueStore.keys(cloudStoreAccount).bind()

      keys
        .filter(::isCloudBackupKey)
        .forEach { key ->
          ubiquitousKeyValueStore.removeString(cloudStoreAccount, key)
            .bind()
        }
    }

  private suspend fun clearCloudKitStore(
    cloudStoreAccount: CloudStoreAccount,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val iCloudAccount = cloudStoreAccount as? iCloudAccount ?: return@coroutineBinding

      val keys = cloudKitKeyValueStore.keys(iCloudAccount).bind()

      keys
        .filter(::isCloudBackupKey)
        .forEach { key ->
          cloudKitKeyValueStore.remove(iCloudAccount, key)
            .bind()
        }
    }

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)

  private fun usesFakeCloud(account: CloudStoreAccount): Boolean =
    account.cloudStoreAccountRouting(accountConfigService.isFakeCloudStoreActive).useFakeStore
}
