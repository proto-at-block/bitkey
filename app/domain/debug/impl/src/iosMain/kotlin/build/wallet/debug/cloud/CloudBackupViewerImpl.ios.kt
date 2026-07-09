package build.wallet.debug.cloud

import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.UbiquitousKeyValueStore
import build.wallet.cloud.store.cloudStoreAccountRouting
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.isEnabled
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class CloudBackupViewerImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val ubiquitousKeyValueStore: UbiquitousKeyValueStore,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val cloudBackupStore: CloudBackupStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
  private val iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
  private val accountConfigService: AccountConfigService,
) : CloudBackupViewer {
  override suspend fun load(): Result<CloudBackupViewerData, CloudBackupViewerLoadError> =
    loadCloudBackupViewerData(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      iosCloudKitBackupEnabled = iosCloudKitBackupFeatureFlag.isEnabled(),
      storeTypes = { cloudStoreAccount ->
        if (usesFakeCloud(cloudStoreAccount)) {
          listOf(UbiquitousKvs)
        } else {
          availableCloudBackupStoreTypes()
        }
      },
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
      if (usesFakeCloud(cloudStoreAccount)) {
        return@deleteCloudBackupViewerEntry cloudBackupStore.remove(cloudStoreAccount, key)
          .mapError { Error("Failed to delete key '$key'", it) }
      }

      when (storeType) {
        UbiquitousKvs ->
          ubiquitousKeyValueStore.removeString(cloudStoreAccount, key)
            .mapError { Error("Failed to delete key '$key'", it) }
        CloudKit ->
          coroutineBinding {
            val iCloudStoreAccount = ensureNotNull(cloudStoreAccount as? iCloudAccount) {
              Error("CloudKit requires an iCloud account")
            }

            cloudKitKeyValueStore.remove(iCloudStoreAccount, key)
              .mapError { Error("Failed to delete key '$key'", it) }
              .bind()
          }
        else -> Err(Error("Unsupported store type: ${storeType.name}"))
      }
    }

  private suspend fun loadStore(
    storeType: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ): CloudBackupStoreData =
    if (usesFakeCloud(cloudStoreAccount)) {
      loadFakeCloudBackupStore(storeType, cloudStoreAccount)
    } else {
      when (storeType) {
        UbiquitousKvs -> loadUbiquitousKvsStore(cloudStoreAccount)
        CloudKit -> loadCloudKitStore(cloudStoreAccount)
        else ->
          CloudBackupStoreData(
            storeType = storeType,
            entries = emptyList(),
            errorMessage = "Unsupported store type: ${storeType.name}"
          )
      }
    }

  private suspend fun loadFakeCloudBackupStore(
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

  private suspend fun loadUbiquitousKvsStore(
    cloudStoreAccount: CloudStoreAccount,
  ): CloudBackupStoreData {
    val iCloudStoreAccount = cloudStoreAccount as? iCloudAccount
      ?: return CloudBackupStoreData(
        storeType = UbiquitousKvs,
        entries = emptyList(),
        errorMessage = "Ubiquitous KVS requires an iCloud account"
      )

    return loadCloudBackupStoreData(
      storeType = UbiquitousKvs,
      isCloudBackupKey = ::isCloudBackupKey,
      listKeys = {
        ubiquitousKeyValueStore.keys(cloudStoreAccount)
          .mapError { Error("Failed to list keys", it) }
      },
      readValue = { key ->
        ubiquitousKeyValueStore.getString(iCloudStoreAccount, key)
          .mapError { Error("Failed to read key '$key'", it) }
      }
    )
  }

  private suspend fun loadCloudKitStore(
    cloudStoreAccount: CloudStoreAccount,
  ): CloudBackupStoreData {
    val iCloudStoreAccount = cloudStoreAccount as? iCloudAccount
      ?: return CloudBackupStoreData(
        storeType = CloudKit,
        entries = emptyList(),
        errorMessage = "CloudKit requires an iCloud account"
      )

    return loadCloudBackupStoreData(
      storeType = CloudKit,
      isCloudBackupKey = ::isCloudBackupKey,
      listKeys = {
        cloudKitKeyValueStore.keys(iCloudStoreAccount)
          .mapError { Error("Failed to list keys", it) }
          .map { it.toList() }
      },
      readValue = { key ->
        cloudKitKeyValueStore.get(iCloudStoreAccount, key)
          .mapError { Error("Failed to read key '$key'", it) }
          .map { it?.utf8() }
      }
    )
  }

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)

  private fun usesFakeCloud(account: CloudStoreAccount): Boolean =
    account.cloudStoreAccountRouting(accountConfigService.isFakeCloudStoreActive).useFakeStore
}
