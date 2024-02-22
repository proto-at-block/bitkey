package build.wallet.cloud.backup.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.isFullAccount
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.emergencyaccesskit.EmergencyAccessKitPdfGenerator
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepository
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.toErrorIfNull

// TODO(796): add integration tests
class FullAccountCloudBackupRepairerImpl(
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyAccessKitPdfGenerator: EmergencyAccessKitPdfGenerator,
  private val emergencyAccessKitRepository: EmergencyAccessKitRepository,
) : FullAccountCloudBackupRepairer {
  override suspend fun attemptRepair(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    cloudBackupStatus: CloudBackupStatus,
  ) {
    log { "Attempting to repair cloud backup issues" }

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
      log { "Local backup is not for full account" }
      return
    }

    val (mobileKeyBackupStatus, eakBackupStatus) = cloudBackupStatus

    // Attempt to fix Mobile Key Backup
    when (mobileKeyBackupStatus) {
      MobileKeyBackupStatus.ProblemWithBackup.BackupMissing ->
        uploadMobileKeyBackup(account, cloudStoreAccount, localBackup)
      is MobileKeyBackupStatus.ProblemWithBackup.InvalidBackup -> {
        if (localBackup.accountId != mobileKeyBackupStatus.cloudBackup.accountId) {
          log { "Local backup account id does not match invalid backup account id" }
          // We cannot safely assume that the customer would want to overwrite the cloud backup,
          // so let the customer resolve this manually.
          // No action taken here.
        } else {
          // The cloud backup belongs to the customer but is invalid. Attempt to re-upload backup.
          uploadMobileKeyBackup(account, cloudStoreAccount, localBackup)
        }
      }
      // Cannot auto repair other problems with Mobile Key Backup.
      // Customer will have to resolve this manually.
      else -> Unit
    }

    // Attempt to fix Emergency Access Kit
    when (eakBackupStatus) {
      EakBackupStatus.ProblemWithBackup.BackupMissing ->
        uploadEakBackup(account, cloudStoreAccount, localBackup)
      // Cannot auto repair other problems with Emergency Access Kit.
      // Customer will have to resolve this manually.
      else -> Unit
    }
  }

  private suspend fun uploadMobileKeyBackup(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    // Attempt to re-upload backup
    cloudBackupRepository
      .writeBackup(account.accountId, cloudStoreAccount, localBackup)
      .onSuccess {
        log { "Successfully uploaded backup" }
      }
      // Customer will have to resolve this manually
      .logFailure { "Error uploading cloud backup" }
  }

  private suspend fun uploadEakBackup(
    account: FullAccount,
    cloudStoreAccount: CloudStoreAccount,
    localBackup: CloudBackup,
  ) {
    val sealedCsek = when (localBackup) {
      is CloudBackupV2 ->
        localBackup.fullAccountFields?.hwEncryptionKeyCiphertext
          // The backup should be for full account, but to be safe.
          ?: return
    }

    emergencyAccessKitPdfGenerator
      .generate(account.keybox, sealedCsek)
      .flatMap { eakData ->
        emergencyAccessKitRepository.write(cloudStoreAccount, eakData)
      }
      .onSuccess {
        log { "Successfully uploaded Emergency Access Kit" }
      }
      // Customer will have to resolve this manually
      .logFailure { "Error uploading Emergency Access Kit" }
  }
}
