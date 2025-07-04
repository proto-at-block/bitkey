package build.wallet.cloud.backup.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.isFullAccount
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitPdfGenerator
import build.wallet.emergencyexitkit.EmergencyExitKitRepository
import build.wallet.logging.*
import build.wallet.logging.logFailure
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull

// TODO(796): add integration tests

@BitkeyInject(AppScope::class)
class FullAccountCloudBackupRepairerImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyExitKitPdfGenerator: EmergencyExitKitPdfGenerator,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
) : FullAccountCloudBackupRepairer {
  override suspend fun attemptRepair(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  ) {
    logDebug { "Attempting to repair cloud backup issues" }

    val localBackup = cloudBackupDao
      .get(account.accountId.serverId)
      .toErrorIfNull { Error("No local backup found") }
      .logFailure { "Error finding local backup" }
      .get()
      // We don't have a local backup, so we can't compare it to the cloud backup.
      // Customer will have to resolve this manually.
      ?: return

    // Ensure local backup is for full account. Should not happen, but to be safe.
    if (!localBackup.isFullAccount()) {
      logWarn { "Local backup is not for full account" }
      return
    }

    val (appKeyBackupStatus, eekBackupStatus) = cloudBackupStatus

    // Attempt to fix App Key Backup
    when (appKeyBackupStatus) {
      AppKeyBackupStatus.ProblemWithBackup.BackupMissing ->
        uploadAppKeyBackup(account, cloudStoreAccount, localBackup)
      is AppKeyBackupStatus.ProblemWithBackup.InvalidBackup -> {
        if (localBackup.accountId != appKeyBackupStatus.cloudBackup.accountId) {
          logWarn { "Local backup account id does not match invalid backup account id" }
          // We cannot safely assume that the customer would want to overwrite the cloud backup,
          // so let the customer resolve this manually.
          // No action taken here.
        } else {
          // The cloud backup belongs to the customer but is invalid. Attempt to re-upload backup.
          uploadAppKeyBackup(account, cloudStoreAccount, localBackup)
        }
      }
      // Cannot auto repair other problems with App Key Backup.
      // Customer will have to resolve this manually.
      else -> Unit
    }

    // Attempt to fix Emergency Exit Kit
    when (eekBackupStatus) {
      EekBackupStatus.ProblemWithBackup.BackupMissing ->
        uploadEekBackup(account, cloudStoreAccount, localBackup)
      // Cannot auto repair other problems with Emergency Exit Kit.
      // Customer will have to resolve this manually.
      else -> Unit
    }
  }

  private suspend fun uploadAppKeyBackup(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    // Attempt to re-upload backup
    cloudBackupRepository
      .writeBackup(account.accountId, cloudStoreAccount, localBackup, true)
      .onSuccess {
        logInfo {
          "Cloud backup uploaded via FullAccountCloudBackupRepairer"
        }
      }
      // Customer will have to resolve this manually
      .logFailure { "Error uploading cloud backup" }
  }

  private suspend fun uploadEekBackup(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    val sealedCsek = when (localBackup) {
      is CloudBackupV2 ->
        localBackup.fullAccountFields?.sealedHwEncryptionKey
          // The backup should be for full account, but to be safe.
          ?: return
    }

    emergencyExitKitPdfGenerator
      .generate(account.keybox, sealedCsek)
      .flatMap { eekData ->
        emergencyExitKitRepository.write(cloudStoreAccount, eekData)
      }
      .onSuccess {
        logDebug { "Successfully uploaded Emergency Exit Kit" }
      }
      // Customer will have to resolve this manually
      .logFailure { "Error uploading Emergency Exit Kit" }
  }
}
