package build.wallet.debug.cloud

import build.wallet.bitkey.f8e.AccountId
import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.backup.CloudBackupStoreKeys
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SealedData
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.orElse
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class CloudBackupCorrupterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupStore: CloudBackupStore,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupStoreKeys: CloudBackupStoreKeys,
  private val cloudBackupDao: CloudBackupDao,
) : CloudBackupCorrupter {
  val sealedDataMock =
    SealedData(
      ciphertext = "deadbeef".decodeHex(),
      nonce = "abcdef".decodeHex(),
      tag = "123456".decodeHex()
    )

  override suspend fun corrupt(accountId: AccountId): Result<Unit, CorruptionError> =
    coroutineBinding {
      ensure(appVariant != Customer) {
        CorruptionError.CustomerBuild("Not allowed to corrupt cloud backups in Customer builds.")
      }

      val nullableCloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { CorruptionError.CloudAccountError("Failed to get cloud account", it) }
        .bind()
      val cloudAccount = ensureNotNull(nullableCloudAccount) {
        CorruptionError.CloudAccountError("No cloud account")
      }

      val backupKeys = cloudBackupStore.keys(cloudAccount)
        .mapError { CorruptionError.BackupReadError("Failed to list backups", it) }
        .bind()

      var corruptedBackup = false
      for (key in backupKeys.activeBackupCandidateKeys(accountId)) {
        corruptedBackup = readBackupThenCorrupt(cloudAccount, key, accountId).bind()
        if (corruptedBackup) {
          break
        }
      }

      ensure(corruptedBackup) {
        CorruptionError.BackupNotFoundError("No cloud backup found for active account.")
      }
    }

  private fun List<String>.activeBackupCandidateKeys(accountId: AccountId): List<String> =
    (
      filter { key -> cloudBackupStoreKeys.isAccountSpecificActiveBackupKeyForAccount(key, accountId) } +
        filter { key -> cloudBackupStoreKeys.isLegacyActiveBackupKey(key) }
    ).distinct()

  /**
   * Read the existing backup then save the corrupted version if it belongs to [accountId].
   */
  private suspend fun readBackupThenCorrupt(
    cloudAccount: CloudStoreAccount,
    key: String,
    accountId: AccountId,
  ): Result<Boolean, CorruptionError> =
    coroutineBinding {
      val backupJson = cloudBackupStore.get(cloudAccount, key)
        .mapError { CorruptionError.BackupReadError("Failed to get backup", it) }
        .bind()
        ?.utf8()

      if (backupJson == null) {
        false
      } else {
        val (corruptedBackupJson, corruptedBackup) = corruptBackupJson(backupJson).bind()

        if (corruptedBackup.accountId != accountId.serverId) {
          false
        } else {
          // Write the corrupted backup back to cloud storage
          cloudBackupStore.set(
            cloudAccount,
            key,
            corruptedBackupJson.encodeUtf8()
          )
            .mapError { CorruptionError.BackupWriteError("Failed to corrupt backup", it) }
            .bind()

          // Keep local backup in sync so health auto-repair does not immediately undo corruption.
          cloudBackupDao.set(corruptedBackup.accountId, corruptedBackup)
            .mapError {
              CorruptionError.BackupWriteError("Failed to persist corrupted local backup", it)
            }
            .bind()

          true
        }
      }
    }

  private fun corruptBackupJson(
    backupJson: String,
  ): Result<Pair<String, CloudBackup>, CorruptionError> =
    catchingResult {
      val existingBackup = Json.decodeFromString<CloudBackupV3>(backupJson)
      val corruptedBackup = existingBackup.copy(
        fullAccountFields = existingBackup.fullAccountFields?.copy(
          hwFullAccountKeysCiphertext = sealedDataMock
        )
      )

      Json.encodeToString(CloudBackupV3.serializer(), corruptedBackup) to corruptedBackup
    }
      .orElse {
        catchingResult {
          val existingBackup = Json.decodeFromString<CloudBackupV2>(backupJson)
          val corruptedBackup = existingBackup.copy(
            fullAccountFields = existingBackup.fullAccountFields?.copy(
              hwFullAccountKeysCiphertext = sealedDataMock
            )
          )

          Json.encodeToString(CloudBackupV2.serializer(), corruptedBackup) to corruptedBackup
        }
      }
      .mapError { CorruptionError.DeserializationError("Failed to corrupt backup", it) }
}
