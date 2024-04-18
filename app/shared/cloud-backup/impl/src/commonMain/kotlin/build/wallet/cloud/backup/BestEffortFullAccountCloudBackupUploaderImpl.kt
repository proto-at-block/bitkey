package build.wallet.cloud.backup

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.BreakingError
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.IgnorableError
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull
import com.github.michaelbull.result.toResultOr
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

class BestEffortFullAccountCloudBackupUploaderImpl(
  private val cloudBackupDao: CloudBackupDao,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val cloudBackupRepository: CloudBackupRepository,
) : BestEffortFullAccountCloudBackupUploader {
  override suspend fun createAndUploadCloudBackup(
    fullAccount: FullAccount,
  ): Result<Unit, Failure> =
    binding {
      val trustedContacts = socRecRelationshipsRepository
        .relationships
        .filterNotNull()
        .firstOrNull()
        .toResultOr { BreakingError("Error reading trusted contacts") }
        .bind()
        .endorsedTrustedContacts

      val currentCloudBackup = cloudBackupDao
        .get(accountId = fullAccount.accountId.serverId)
        .toErrorIfNull { IllegalStateException("Error getting cloud backup") }
        .logFailure(LogLevel.Warn) { "Could not get current cloud backup" }
        .mapError { IgnorableError("Error getting cloud backup", it) }
        .bind()

      val hwekEncryptedPkek = currentCloudBackup
        .getHwekEncryptedPkek()
        .mapError { BreakingError("Error getting hwek encrypted pkek", it) }
        .bind()

      val newCloudBackup =
        fullAccountCloudBackupCreator
          .create(
            keybox = fullAccount.keybox,
            sealedCsek = hwekEncryptedPkek,
            endorsedTrustedContacts = trustedContacts
          )
          .logFailure(LogLevel.Warn) { "Could not create cloud backup" }
          .mapError { BreakingError("Error creating cloud backup", it) }
          .bind()

      // Write here early so that if the upload fails, cloud backup health still sees new backup.
      cloudBackupDao.set(accountId = fullAccount.accountId.serverId, backup = newCloudBackup)
        .logFailure(LogLevel.Warn) { "Could not set cloud backup" }
        .mapError { IgnorableError("Error setting cloud backup", it) }
        .onSuccess {
          log { "Cloud backup stored locally" }
        }
        .bind()

      val cloudStoreAccount =
        cloudStoreAccountRepository
          .currentAccount(cloudServiceProvider())
          .toErrorIfNull { IllegalStateException("No cloud store account") }
          .logFailure(LogLevel.Warn) { "Could not get cloud store account" }
          .mapError { IgnorableError("Error getting cloud store account", it) }
          .bind()

      // Upload new cloud backup.
      cloudBackupRepository.writeBackup(
        accountId = fullAccount.accountId,
        cloudStoreAccount = cloudStoreAccount,
        backup = newCloudBackup,
        requireAuthRefresh = true
      )
        .logFailure(LogLevel.Warn) { "Could not upload cloud backup" }
        .mapError { IgnorableError("Error uploading cloud backup", it) }
        .onSuccess {
          log { "Cloud backup uploaded" }
        }
        .bind()
    }
}

private fun CloudBackup.getHwekEncryptedPkek(): Result<SealedCsek, Error> {
  // Using `when` to catch if/when CloudBackupV2 happens.
  return when (this) {
    is CloudBackupV2 -> fullAccountFields?.sealedHwEncryptionKey?.let { Ok(it) }
      ?: Err(Error("Lite Account backup found"))
  }
}
