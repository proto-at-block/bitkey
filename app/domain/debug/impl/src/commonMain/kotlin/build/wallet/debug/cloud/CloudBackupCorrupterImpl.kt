package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SealedData
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.onSuccess
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeHex

@BitkeyInject(AppScope::class)
class CloudBackupCorrupterImpl(
  private val appVariant: AppVariant,
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : CloudBackupCorrupter {
  // Key used to store backups in cloud key-value store
  private val cloudBackupKey = "cloud-backup"

  val sealedDataMock =
    SealedData(
      ciphertext = "deadbeef".decodeHex(),
      nonce = "abcdef".decodeHex(),
      tag = "123456".decodeHex()
    )

  override suspend fun corrupt() {
    check(appVariant != Customer) {
      "Not allowed to corrupt cloud backups in Customer builds."
    }

    cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .onSuccess { cloudAccount ->
        cloudAccount?.let {
          // Read the existing backup
          cloudKeyValueStore.getString(cloudAccount, cloudBackupKey)
            .onSuccess { backupJson ->
              if (backupJson != null) {
                // Deserialize the existing backup
                val existingBackup = Json.decodeFromString<CloudBackupV2>(backupJson)

                // Create a copy with fullAccountFields set to null
                val corruptedBackup = existingBackup.copy(fullAccountFields = existingBackup.fullAccountFields?.copy(hwFullAccountKeysCiphertext = sealedDataMock))

                // Serialize the corrupted backup
                val corruptedBackupJson = Json.encodeToString(CloudBackupV2.serializer(), corruptedBackup)

                // Write the corrupted backup back to cloud storage
                cloudKeyValueStore.setString(
                  cloudAccount,
                  cloudBackupKey,
                  corruptedBackupJson
                )
              }
            }
        }
      }
  }
}
