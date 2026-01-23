package build.wallet.debug.cloud

import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackupRepositoryKeys
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SealedData
import build.wallet.ensure
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.orElse
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeHex

@BitkeyInject(AppScope::class)
class CloudBackupCorrupterImpl(
  private val appVariant: AppVariant,
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepositoryKeys: CloudBackupRepositoryKeys,
) : CloudBackupCorrupter {
  val sealedDataMock =
    SealedData(
      ciphertext = "deadbeef".decodeHex(),
      nonce = "abcdef".decodeHex(),
      tag = "123456".decodeHex()
    )

  override suspend fun corrupt(): Result<Unit, CorruptionError> =
    coroutineBinding {
      ensure(appVariant != Customer) {
        CorruptionError.CustomerBuild("Not allowed to corrupt cloud backups in Customer builds.")
      }

      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { CorruptionError.CloudAccountError("Failed to get cloud account", it) }
        .bind()

      cloudAccount?.let {
        cloudKeyValueStore.keys(cloudAccount)
          .onSuccess {
            it.asSequence()
              .filter { key -> cloudBackupRepositoryKeys.isValidBackupKey(key) }
              .forEach { key -> readBackupThenCorrupt(cloudAccount, key).bind() }
          }
      }
    }

  /**
   * Read the existing backup then save the corrupted version.
   */
  private suspend fun readBackupThenCorrupt(
    cloudAccount: CloudStoreAccount,
    key: String,
  ): Result<Unit, CorruptionError> =
    coroutineBinding {
      val backupJson = cloudKeyValueStore.getString(cloudAccount, key)
        .mapError { CorruptionError.BackupReadError("Failed to get backup", it) }
        .bind()

      if (backupJson != null) {
        // Deserialize the existing backup
        val corruptedBackupJson = catchingResult {
          val existingBackup = Json.decodeFromString<CloudBackupV3>(backupJson)

          // Create a copy with fullAccountFields set to null
          val corruptedBackup = existingBackup.copy(
            fullAccountFields = existingBackup.fullAccountFields?.copy(
              hwFullAccountKeysCiphertext = sealedDataMock
            )
          )

          // Serialize the corrupted backup
          Json.encodeToString(CloudBackupV3.serializer(), corruptedBackup)
        }
          .orElse {
            // If deserialization as V3 fails, try V2
            catchingResult {
              val existingBackup = Json.decodeFromString<CloudBackupV2>(backupJson)

              // Create a copy with fullAccountFields set to null
              val corruptedBackup = existingBackup.copy(
                fullAccountFields = existingBackup.fullAccountFields?.copy(
                  hwFullAccountKeysCiphertext = sealedDataMock
                )
              )

              // Serialize the corrupted backup
              Json.encodeToString(CloudBackupV2.serializer(), corruptedBackup)
            }
          }
          .mapError { CorruptionError.DeserializationError("Failed to corrupt backup", it) }
          .bind()

        // Write the corrupted backup back to cloud storage
        cloudKeyValueStore.setString(
          cloudAccount,
          key,
          corruptedBackupJson
        )
          .mapError { CorruptionError.BackupWriteError("Failed to corrupt backup", it) }
          .bind()
      }
    }
}
