package build.wallet.debug.cloud

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.keybox.KeyboxDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class CloudBackupKeysetDeleterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val keyboxDao: KeyboxDao,
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
) : CloudBackupKeysetDeleter {
  override suspend fun deleteActiveKeyset(): Result<Unit, KeysetDeletionError> =
    coroutineBinding {
      check(appVariant != Customer) { "Not allowed in Customer builds." }

      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { KeysetDeletionError.CloudAccountError("Failed to get cloud account", it) }
        .bind()
        ?: error("No cloud account")

      val backup = cloudBackupRepository.readActiveBackup(cloudAccount)
        .mapError { KeysetDeletionError.BackupReadError("Failed to read backup", it) }
        .bind()
        ?: error("No backup found")

      val keybox = keyboxDao.getActiveOrOnboardingKeybox()
        .getOrElse { null }
        ?: error("No active keybox")

      val modifiedBackup = modifyBackup(backup).bind()

      cloudBackupRepository.writeBackup(
        accountId = keybox.fullAccountId,
        cloudStoreAccount = cloudAccount,
        backup = modifiedBackup,
        requireAuthRefresh = false
      ).mapError { KeysetDeletionError.BackupWriteError("Failed to write backup", it) }.bind()
    }

  private suspend fun modifyBackup(backup: CloudBackup): Result<CloudBackup, KeysetDeletionError> =
    coroutineBinding {
      val fields = when (backup) {
        is CloudBackupV3 -> backup.fullAccountFields
        is CloudBackupV2 -> backup.fullAccountFields
      } ?: error("No full account fields")

      val csek = csekDao.get(fields.sealedHwEncryptionKey)
        .mapError { KeysetDeletionError.DecryptionError("Failed to get CSEK", it) }
        .toErrorIfNull { KeysetDeletionError.PkekMissingError("CSEK not found") }
        .bind()

      val decrypted = symmetricKeyEncryptor.unsealNoMetadata(fields.hwFullAccountKeysCiphertext, csek.key)
      val keys = Json.decodeFromString<FullAccountKeys>(decrypted.utf8())

      val previousKeyset = keys.keysets.lastOrNull { it.localId != keys.activeSpendingKeyset.localId }
        ?: error("No previous keyset available")

      val modifiedKeys = keys.copy(
        activeSpendingKeyset = previousKeyset,
        appSpendingKeys = keys.appSpendingKeys.filterKeys { it != keys.activeSpendingKeyset.appKey }
      )

      val encrypted = symmetricKeyEncryptor.sealNoMetadata(
        Json.encodeToString(FullAccountKeys.serializer(), modifiedKeys).encodeUtf8(),
        csek.key
      )

      val modifiedFields = fields.copy(hwFullAccountKeysCiphertext = encrypted)

      when (backup) {
        is CloudBackupV3 -> backup.copy(fullAccountFields = modifiedFields)
        is CloudBackupV2 -> backup.copy(fullAccountFields = modifiedFields)
      }
    }
}
