package build.wallet.cloud.backup.health

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
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
  private val cloudBackupService: CloudBackupService,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyExitKitPdfGenerator: EmergencyExitKitPdfGenerator,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
) : FullAccountCloudBackupRepairer {
  override suspend fun attemptRepair(
    accountId: FullAccountId,
    keybox: Keybox,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  ) {
    logDebug { "Attempting to repair cloud backup issues" }

    val localBackup = cloudBackupDao
      .get(accountId.serverId)
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
      AppKeyBackupStatus.ProblemWithBackup.BackupMissing, AppKeyBackupStatus.ProblemWithBackup.StaleBackup ->
        uploadAppKeyBackup(accountId, cloudStoreAccount, localBackup)
      is AppKeyBackupStatus.ProblemWithBackup.InvalidBackup -> {
        if (localBackup.accountId != appKeyBackupStatus.cloudBackup.accountId) {
          logWarn { "Local backup account id does not match invalid backup account id" }
          // We cannot safely assume that the customer would want to overwrite the cloud backup,
          // so let the customer resolve this manually.
          // No action taken here.
        } else {
          // The cloud backup belongs to the customer but is invalid. Attempt to re-upload backup.
          uploadAppKeyBackup(accountId, cloudStoreAccount, localBackup)
        }
      }
      // Cannot auto repair other problems with App Key Backup.
      // Customer will have to resolve this manually.
      else -> Unit
    }

    // Attempt to fix Emergency Exit Kit
    when (eekBackupStatus) {
      EekBackupStatus.ProblemWithBackup.BackupMissing ->
        uploadEekBackup(keybox, cloudStoreAccount, localBackup)
      // Cannot auto repair other problems with Emergency Exit Kit.
      // Customer will have to resolve this manually.
      else -> Unit
    }
  }

  private suspend fun uploadAppKeyBackup(
    accountId: FullAccountId,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    // Attempt to re-upload backup
    cloudBackupService
      .writeBackup(accountId, cloudStoreAccount, localBackup, true)
      .onSuccess {
        logInfo {
          "Cloud backup uploaded via FullAccountCloudBackupRepairer"
        }
      }
      // Customer will have to resolve this manually
      .logFailure { "Error uploading cloud backup" }
  }

  private suspend fun uploadEekBackup(
    keybox: Keybox,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    val sealedCsek = when (localBackup) {
      is CloudBackupV3 ->
        localBackup.fullAccountFields?.sealedHwEncryptionKey
          // The backup should be for full account, but to be safe.
          ?: return
      is CloudBackupV2 ->
        localBackup.fullAccountFields?.sealedHwEncryptionKey
          // The backup should be for full account, but to be safe.
          ?: return
    }

    emergencyExitKitPdfGenerator
      .generate(keybox, sealedCsek)
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
