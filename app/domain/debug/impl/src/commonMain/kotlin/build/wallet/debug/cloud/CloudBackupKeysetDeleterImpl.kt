package build.wallet.debug.cloud

import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupService
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
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class CloudBackupKeysetDeleterImpl(
  private val appVariant: AppVariant,
  private val cloudBackupService: CloudBackupService,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val keyboxDao: KeyboxDao,
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
) : CloudBackupKeysetDeleter {
  override suspend fun deleteActiveKeyset(): Result<Unit, KeysetDeletionError> =
    coroutineBinding {
      ensure(appVariant != Customer) {
        KeysetDeletionError.CustomerBuild("Not allowed in Customer builds.")
      }

      val cloudAccount = cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
        .mapError { KeysetDeletionError.CloudAccountError("Failed to get cloud account", it) }
        .toErrorIfNull { KeysetDeletionError.CloudAccountError("No cloud account") }
        .bind()

      val backup = cloudBackupService.readActiveBackup(cloudAccount)
        .mapError { KeysetDeletionError.BackupReadError("Failed to read backup", it) }
        .toErrorIfNull { KeysetDeletionError.BackupReadError("No backup found") }
        .bind()

      val keybox = keyboxDao.getActiveOrOnboardingKeybox()
        .mapError { KeysetDeletionError.DecryptionError("Failed to get active keybox", it) }
        .toErrorIfNull { KeysetDeletionError.DecryptionError("No active keybox") }
        .bind()

      val modifiedBackup = modifyBackup(backup).bind()

      cloudBackupService.writeBackup(
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
      }
      val fullAccountFields = ensureNotNull(fields) {
        KeysetDeletionError.DecryptionError("No full account fields")
      }

      val csek = csekDao.get(fullAccountFields.sealedHwEncryptionKey)
        .mapError { KeysetDeletionError.DecryptionError("Failed to get CSEK", it) }
        .toErrorIfNull { KeysetDeletionError.PkekMissingError("CSEK not found") }
        .bind()

      val decrypted = symmetricKeyEncryptor.unsealNoMetadata(
        fullAccountFields.hwFullAccountKeysCiphertext,
        csek.key
      )
      val keys = Json.decodeFromString<FullAccountKeys>(decrypted.utf8())

      val previousKeyset = ensureNotNull(
        keys.keysets.lastOrNull { it.localId != keys.activeSpendingKeyset.localId }
      ) {
        KeysetDeletionError.DecryptionError("No previous keyset available")
      }

      val modifiedKeys = keys.copy(
        activeSpendingKeyset = previousKeyset,
        appSpendingKeys = keys.appSpendingKeys.filterKeys { it != keys.activeSpendingKeyset.appKey }
      )

      val encrypted = symmetricKeyEncryptor.sealNoMetadata(
        Json.encodeToString(FullAccountKeys.serializer(), modifiedKeys).encodeUtf8(),
        csek.key
      )

      val modifiedFields = fullAccountFields.copy(hwFullAccountKeysCiphertext = encrypted)

      when (backup) {
        is CloudBackupV3 -> backup.copy(fullAccountFields = modifiedFields)
        is CloudBackupV2 -> backup.copy(fullAccountFields = modifiedFields)
      }
    }
}
