package build.wallet.cloud.backup

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.BreakingError
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.IgnorableError
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.logging.LogLevel
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class BestEffortFullAccountCloudBackupUploaderImpl(
  private val cloudBackupDao: CloudBackupDao,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val cloudBackupRepository: CloudBackupRepository,
) : BestEffortFullAccountCloudBackupUploader {
  override suspend fun createAndUploadCloudBackup(
    fullAccount: FullAccount,
  ): Result<Unit, Failure> =
    coroutineBinding {
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
            sealedCsek = hwekEncryptedPkek
          )
          .logFailure(LogLevel.Warn) { "Could not create cloud backup" }
          .mapError { BreakingError("Error creating cloud backup", it) }
          .bind()

      // Write here early so that if the upload fails, cloud backup health still sees new backup.
      cloudBackupDao.set(accountId = fullAccount.accountId.serverId, backup = newCloudBackup)
        .logFailure(LogLevel.Warn) { "Could not set cloud backup" }
        .mapError { IgnorableError("Error setting cloud backup", it) }
        .onSuccess {
          logDebug { "Cloud backup stored locally" }
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
          logInfo { "Cloud backup uploaded via BestEffortFullAccountCloudBackupUploader" }
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
