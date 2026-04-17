package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.ensure
import build.wallet.ensureNotNull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError

internal class SingleStoreCloudBackupViewer(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
) : CloudBackupViewer {
  override suspend fun load(): Result<CloudBackupViewerData, CloudBackupViewerLoadError> =
    loadCloudBackupViewerData(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      iosCloudKitBackupEnabled = null,
      loadStore = { storeType, cloudStoreAccount ->
        loadCloudBackupStoreData(
          storeType = storeType,
          isCloudBackupKey = ::isCloudBackupKey,
          listKeys = {
            cloudKeyValueStore.keys(cloudStoreAccount)
              .mapError { Error("Failed to list keys", it) }
          },
          readValue = { key ->
            cloudKeyValueStore.getString(cloudStoreAccount, key)
              .mapError { Error("Failed to read key '$key'", it) }
          }
        )
      }
    )

  override suspend fun deleteEntry(
    storeType: CloudBackupStoreType,
    key: String,
  ): Result<Unit, Error> =
    deleteCloudBackupViewerEntry(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      storeType = storeType
    ) { cloudStoreAccount ->
      cloudKeyValueStore.removeString(cloudStoreAccount, key)
        .mapError { Error("Failed to delete key '$key'", it) }
    }

  private fun isCloudBackupKey(key: String): Boolean =
    cloudBackupStoreKeys.isValidBackupKey(key) || cloudBackupStoreKeys.isValidArchivedKey(key)
}

internal suspend fun loadCloudBackupViewerData(
  cloudStoreAccountRepository: CloudStoreAccountRepository,
  iosCloudKitBackupEnabled: Boolean?,
  loadStore: suspend (
    storeType: CloudBackupStoreType,
    cloudStoreAccount: CloudStoreAccount,
  ) -> CloudBackupStoreData,
): Result<CloudBackupViewerData, CloudBackupViewerLoadError> =
  coroutineBinding {
    val cloudStoreAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .mapError { error ->
        CloudBackupViewerLoadError(
          message = "Failed to load cloud account: ${error.message ?: "Unknown error"}",
          cause = error
        )
      }
      .bind()

    if (cloudStoreAccount == null) {
      return@coroutineBinding CloudBackupViewerData.NoCloudAccount
    }

    val stores = availableCloudBackupStoreTypes().map { storeType ->
      loadStore(storeType, cloudStoreAccount)
    }

    CloudBackupViewerData.Loaded(
      iosCloudKitBackupEnabled = iosCloudKitBackupEnabled,
      stores = stores
    )
  }

internal suspend fun deleteCloudBackupViewerEntry(
  cloudStoreAccountRepository: CloudStoreAccountRepository,
  storeType: CloudBackupStoreType,
  delete: suspend (cloudStoreAccount: CloudStoreAccount) -> Result<Unit, Error>,
): Result<Unit, Error> =
  coroutineBinding {
    ensure(availableCloudBackupStoreTypes().contains(storeType)) {
      Error("Unsupported store type: ${storeType.name}")
    }

    val cloudStoreAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .mapError { Error("Failed to load cloud account", it) }
      .bind()

    val account = ensureNotNull(cloudStoreAccount) {
      Error("No cloud account is signed in")
    }

    delete(account).bind()
  }

internal suspend fun loadCloudBackupStoreData(
  storeType: CloudBackupStoreType,
  isCloudBackupKey: (String) -> Boolean,
  listKeys: suspend () -> Result<List<String>, Error>,
  readValue: suspend (String) -> Result<String?, Error>,
): CloudBackupStoreData {
  val keys = listKeys().getOrElse { error ->
    return CloudBackupStoreData(
      storeType = storeType,
      entries = emptyList(),
      errorMessage = "Failed to list keys: ${error.message ?: "Unknown error"}"
    )
  }

  var failedReadCount = 0
  val entries = mutableListOf<CloudBackupEntry>()
  keys
    .filter(isCloudBackupKey)
    .sorted()
    .forEach { key ->
      val value = readValue(key).getOrElse { error ->
        failedReadCount += 1
        "Error reading value: ${error.message ?: "Unknown error"}"
      } ?: "null"

      entries += CloudBackupEntry(
        key = key,
        value = value
      )
    }

  return CloudBackupStoreData(
    storeType = storeType,
    entries = entries,
    errorMessage = failedReadEntriesMessage(failedReadCount)
  )
}

private fun failedReadEntriesMessage(failedReadCount: Int): String? =
  if (failedReadCount > 0) {
    "Failed to read $failedReadCount entr${if (failedReadCount == 1) "y" else "ies"}."
  } else {
    null
  }
