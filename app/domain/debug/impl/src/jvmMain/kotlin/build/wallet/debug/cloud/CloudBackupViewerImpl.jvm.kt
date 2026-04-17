package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class CloudBackupViewerImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupStore: CloudBackupStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupViewer {
  override suspend fun load(): Result<CloudBackupViewerData, CloudBackupViewerLoadError> =
    loadCloudBackupViewerData(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      iosCloudKitBackupEnabled = null,
      loadStore = ::loadStore
    )

  override suspend fun deleteEntry(
    storeType: CloudBackupStoreType,
    key: String,
  ): Result<Unit, Error> =
    deleteCloudBackupViewerEntry(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      storeType = storeType
    ) { cloudStoreAccount ->
      cloudBackupStore.remove(cloudStoreAccount, key)
        .mapError { Error("Failed to delete key '$key'", it) }
    }

  private suspend fun loadStore(
    storeType: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): CloudBackupStoreData =
    loadCloudBackupStoreData(
      storeType = storeType,
      isCloudBackupKey = ::isCloudBackupKey,
      listKeys = {
        cloudBackupStore.keys(cloudStoreAccount)
          .mapError { Error("Failed to list keys", it) }
      },
      readValue = { key ->
        cloudBackupStore.get(cloudStoreAccount, key)
          .mapError { Error("Failed to read key '$key'", it) }
          .map { it?.utf8() }
      }
    )

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)
}
