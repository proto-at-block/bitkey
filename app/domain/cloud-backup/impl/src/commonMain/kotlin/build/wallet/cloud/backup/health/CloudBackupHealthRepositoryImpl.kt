package build.wallet.cloud.backup.health

import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.*
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupError
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupOperationLock
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitRepository
import build.wallet.feature.flags.CloudBackupForceReuploadTimestampFeatureFlag
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class CloudBackupHealthRepositoryImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupService: CloudBackupService,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
  private val fullAccountCloudBackupRepairer: FullAccountCloudBackupRepairer,
  private val appFunctionalityService: AppFunctionalityService,
  private val jsonSerializer: JsonSerializer,
  private val cloudBackupForceReuploadTimestampFeatureFlag:
    CloudBackupForceReuploadTimestampFeatureFlag,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val cloudBackupOperationLock: CloudBackupOperationLock,
) : CloudBackupHealthRepository {
  companion object {
    private const val CLOUD_BACKUP_SIZE_LIMIT_BYTES = 1_000_000

  }

  private val appKeyBackupStatus = MutableStateFlow<AppKeyBackupStatus?>(null)

  override fun appKeyBackupStatus(): StateFlow<AppKeyBackupStatus?> {
    return appKeyBackupStatus
  }

  private val eekBackupStatus = MutableStateFlow<EekBackupStatus?>(null)

  override fun eekBackupStatus(): StateFlow<EekBackupStatus?> {
    return eekBackupStatus
  }

  override suspend fun performSync(
    accountId: FullAccountId,
    keybox: Keybox,
  ): CloudBackupStatus {
    // CRITICAL: Do NOT call CloudBackupVersionMigrationWorker.executeWork() or
    // SocRecCloudBackupSyncWorker.refreshCloudBackup() within this block - it would cause a deadlock.
    logDebug { "Starting cloud backup health check" }
    return cloudBackupOperationLock.withLock {
      getCurrentCloudAccount()
        .fold(
          success = { cloudAccount ->
            val cloudBackupStatus = syncBackupStatus(cloudAccount, accountId, keybox)

            if (cloudBackupStatus.isHealthy()) {
              logInfo { "Cloud backup health check completed - backup is healthy" }
              cloudBackupStatus
            } else {
              logInfo {
                "Cloud backup health check found issues - " +
                  "appKeyStatus=${cloudBackupStatus.appKeyBackupStatus::class.simpleName}, " +
                  "eekStatus=${cloudBackupStatus.eekBackupStatus::class.simpleName}"
              }
              // Attempt to repair the backup silently first
              fullAccountCloudBackupRepairer.attemptRepair(accountId, keybox, cloudAccount, cloudBackupStatus)
              // Re-sync and return whatever the status is after repair attempt.
              val postRepairStatus = syncBackupStatus(cloudAccount, accountId, keybox)
              logInfo {
                "Cloud backup health check completed after repair attempt - " +
                  "appKeyStatus=${postRepairStatus.appKeyBackupStatus::class.simpleName}, " +
                  "eekStatus=${postRepairStatus.eekBackupStatus::class.simpleName}"
              }
              postRepairStatus
            }
          },
          failure = {
            logWarn { "Cloud backup health check failed - cannot access cloud account" }
            // If we can't get the cloud account, we can't sync the backup status.
            CloudBackupStatus(
              appKeyBackupStatus = AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
              eekBackupStatus = EekBackupStatus.ProblemWithBackup.NoCloudAccess
            )
          }
        )
        .also {
          appKeyBackupStatus.value = it.appKeyBackupStatus
          eekBackupStatus.value = it.eekBackupStatus
        }
    }
  }

  private suspend fun getCurrentCloudAccount(): Result<CloudStoreAccount, Error> {
    return cloudStoreAccountRepository.currentAccount(cloudServiceProvider())
      .toErrorIfNull { CloudStoreAccountMissingError() }
  }

  private class CloudStoreAccountMissingError : Error()

  private suspend fun syncBackupStatus(
    cloudAccount: CloudStoreAccount,
    accountId: FullAccountId,
    keybox: Keybox,
  ) = CloudBackupStatus(
    appKeyBackupStatus = syncAppKeyBackupStatus(cloudAccount, accountId, keybox),
    eekBackupStatus = syncEekBackupStatus(cloudAccount)
  )

  private suspend fun syncAppKeyBackupStatus(
    cloudAccount: CloudStoreAccount,
    accountId: FullAccountId,
    keybox: Keybox,
  ): AppKeyBackupStatus {
    if (appFunctionalityService.status.value.featureStates.cloudBackupHealth == Unavailable) {
      logDebug { "App key backup status check: connectivity unavailable" }
      return AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
    }

    val localCloudBackup = cloudBackupDao
      .get(accountId.serverId)
      .toErrorIfNull { Error("No local backup found") }
      .logFailure { "Error finding local backup" }
      .get()

    if (localCloudBackup == null) {
      // We are missing a local backup, so we can't validate the integrity of the cloud backup.
      // Mark backup as missing to let the customer
      logInfo { "App key backup status check: no local backup found - marking as missing" }
      return AppKeyBackupStatus.ProblemWithBackup.BackupMissing
    }

    checkAndFixAppGlobalAuthKeySignature(cloudAccount, localCloudBackup, accountId, keybox)
      ?.let { return it }

    checkAndFixBackupSize(localCloudBackup, accountId, keybox)?.let { return it }

    return compareLocalAndCloudBackups(cloudAccount, localCloudBackup)
  }

  /**
   * Checks the local backup's app global auth key hw signature against the active keybox,
   * so that flows relying on the backup (e.g. descriptor backup) don't use stale key material.
   *
   * Repairs only the unambiguous case: the backup holds a sentinel/placeholder signature while
   * the keybox holds a real one. The backup is regenerated, persisted, and marked
   * [AppKeyBackupStatus.ProblemWithBackup.StaleBackup] so the repairer re-uploads it. Any other
   * mismatch (keybox sentinel, hardware type change, real-vs-real) is a transient state owned
   * by a recovery/migration/rotation flow and is left alone.
   */
  private suspend fun checkAndFixAppGlobalAuthKeySignature(
    cloudAccount: CloudStoreAccount,
    localCloudBackup: CloudBackup,
    accountId: FullAccountId,
    keybox: Keybox,
  ): AppKeyBackupStatus? {
    val fullAccountFields = when (localCloudBackup) {
      is CloudBackupV2 -> localCloudBackup.fullAccountFields
      is CloudBackupV3 -> localCloudBackup.fullAccountFields
    }
      // Not a full account backup - nothing to check.
      ?: return null

    if (keybox.appGlobalAuthKeyHwSignature.isPlaceholder) {
      // Keybox has no real hardware signature yet; don't regenerate from an incomplete keybox.
      return null
    }

    if (fullAccountFields.hardwareType != keybox.config.hardwareType) {
      // Backup belongs to different hardware (mid-upgrade); its sealed CSEK can't be reused.
      return null
    }

    if (fullAccountFields.appGlobalAuthKeyHwSignature == keybox.appGlobalAuthKeyHwSignature) {
      return null
    }

    if (!fullAccountFields.appGlobalAuthKeyHwSignature.isPlaceholder) {
      // Two real but different signatures, e.g. mid auth key rotation; the rotation flow
      // refreshes the backup itself.
      logWarn {
        "App key backup hw signature does not match active keybox - leaving for owning flow to refresh"
      }
      return null
    }

    // Confirm the active cloud backup belongs to this account before reporting StaleBackup;
    // the repairer's StaleBackup path uploads unconditionally, which would bypass the
    // account-ID guard that the InvalidBackup path applies for shared cloud accounts.
    val activeCloudBackup = cloudBackupService.readActiveBackup(cloudAccount)
      .logFailure { "Failed to confirm active cloud backup ownership" }
      .getOrElse {
        return AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
      }
    val cloudOwnershipConfirmed =
      activeCloudBackup == null || activeCloudBackup.accountId == accountId.serverId
    if (!cloudOwnershipConfirmed) {
      // Fall through to compareLocalAndCloudBackups, which reports the mismatch as
      // InvalidBackup/NoCloudAccess for manual resolution.
      return null
    }

    logWarn {
      "App key backup has placeholder hw signature - regenerating and marking as stale"
    }

    val fixedBackup = fullAccountCloudBackupCreator.create(
      keybox = keybox,
      sealedCsek = fullAccountFields.sealedHwEncryptionKey
    ).logFailure { "Failed to regenerate cloud backup after hw signature mismatch" }
      .get()
      ?: return AppKeyBackupStatus.ProblemWithBackup.PlaceholderSignatureRepairFailed

    // Only report stale once the fix is persisted; the repairer uploads whatever backup is in
    // the DAO, so reporting stale earlier would re-upload the known-stale backup.
    return cloudBackupDao.set(accountId.serverId, fixedBackup)
      .logFailure { "Failed to persist regenerated cloud backup" }
      .fold(
        success = { AppKeyBackupStatus.ProblemWithBackup.StaleBackup },
        failure = { AppKeyBackupStatus.ProblemWithBackup.PlaceholderSignatureRepairFailed }
      )
  }

  private suspend fun checkAndFixBackupSize(
    localCloudBackup: CloudBackup,
    accountId: FullAccountId,
    keybox: Keybox,
  ): AppKeyBackupStatus? {
    if (!localCloudBackup.isFullAccount() || getBackupSizeBytes(localCloudBackup) < CLOUD_BACKUP_SIZE_LIMIT_BYTES) {
      return null
    }

    val sealedCsek = when (localCloudBackup) {
      is CloudBackupV2 -> localCloudBackup.fullAccountFields?.sealedHwEncryptionKey
      is CloudBackupV3 -> localCloudBackup.fullAccountFields?.sealedHwEncryptionKey
    } ?: return null

    val fixedBackup = fullAccountCloudBackupCreator.create(
      keybox = keybox,
      sealedCsek = sealedCsek
    ).logFailure { "Failed to create fixed cloud backup" }
      .get()
      ?: return null

    cloudBackupDao.set(accountId.serverId, fixedBackup)
    logInfo { "App key backup status check: backup exceeded size limit - fixed and marked as stale" }
    return AppKeyBackupStatus.ProblemWithBackup.StaleBackup
  }

  private fun getBackupSizeBytes(backup: CloudBackup): Int {
    val jsonResult = when (backup) {
      is CloudBackupV2 -> jsonSerializer.encodeToStringResult(backup)
      is CloudBackupV3 -> jsonSerializer.encodeToStringResult(backup)
    }
    return jsonResult
      .map { it.encodeToByteArray().size }
      .getOrElse {
        logWarn(throwable = it) { "Failed to calculate backup size" }
        -1
      }
  }

  private suspend fun compareLocalAndCloudBackups(
    cloudAccount: CloudStoreAccount,
    localCloudBackup: CloudBackup,
  ): AppKeyBackupStatus {
    return cloudBackupService
      .readActiveBackup(cloudAccount)
      .fold(
        success = { cloudBackup ->
          when (cloudBackup) {
            null -> {
              logInfo { "App key backup status check: no cloud backup found" }
              AppKeyBackupStatus.ProblemWithBackup.BackupMissing
            }
            else -> {
              if (cloudBackup != localCloudBackup) {
                logInfo { "App key backup status check: backup mismatch detected" }
                AppKeyBackupStatus.ProblemWithBackup.InvalidBackup(cloudBackup)
              } else {
                // TODO(BKR-1155): do we need to perform additional integrity checks?
                logInfo { "App key backup status check: backup is healthy (local matches cloud)" }

                // For V2 backups, preserve existing behavior: always return healthy with current time
                // For V3 backups, use actual timestamp and check force reupload flag
                when (cloudBackup) {
                  is CloudBackupV2 -> {
                    // V2 backup - no timestamp available, return healthy with current time.
                    // Note: CloudBackupVersionMigrationWorker will migrate V2 to V3 on next app
                    // startup/foreground, and subsequent health checks will then use the actual
                    // V3 timestamp for force reupload evaluation.
                    AppKeyBackupStatus.Healthy(lastUploaded = Clock.System.now())
                  }
                  is CloudBackupV3 -> {
                    // V3 backup - check if forced reupload is needed based on feature flag
                    if (shouldForceReuploadDueToTimestamp(cloudBackup.createdAt)) {
                      logInfo { "App key backup status check: backup needs reupload due to force reupload timestamp" }
                      AppKeyBackupStatus.ProblemWithBackup.StaleBackup
                    } else {
                      AppKeyBackupStatus.Healthy(lastUploaded = cloudBackup.createdAt)
                    }
                  }
                }
              }
            }
          }
        },
        failure = {
          when (it) {
            is CloudBackupError.AccountIdMismatched -> {
              logWarn { "Cloud backup account ID does not match local backup account ID" }
              AppKeyBackupStatus.ProblemWithBackup.InvalidBackup(it.backup)
            }
            else -> {
              // TODO(BKR-1156): handle unknown loading errors
              logWarn { "Failed to read cloud backup during sync: $it" }
              AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
            }
          }
        }
      )
  }

  /**
   * Checks if a forced reupload is required based on the feature flag timestamp.
   * Returns true if the feature flag is set to a valid timestamp and the last uploaded
   * timestamp is older than the flag's threshold.
   */
  private fun shouldForceReuploadDueToTimestamp(lastUploaded: Instant): Boolean {
    val flagValue = cloudBackupForceReuploadTimestampFeatureFlag.flagValue().value.value

    // If flag is empty or whitespace, forced reupload is disabled
    if (flagValue.isBlank()) {
      return false
    }

    // Try to parse the flag value as an Instant
    val forceReuploadThreshold = try {
      Instant.parse(flagValue)
    } catch (e: IllegalArgumentException) {
      logInfo {
        "Invalid timestamp format in cloud-backup-force-reupload-timestamp flag: $flagValue - ${e.message}"
      }
      return false
    }

    // Check if backup is older than threshold
    val shouldForce = lastUploaded < forceReuploadThreshold

    if (shouldForce) {
      logInfo {
        "Backup last uploaded at $lastUploaded is older than force reupload threshold $forceReuploadThreshold"
      }
    }

    return shouldForce
  }

  private suspend fun syncEekBackupStatus(cloudAccount: CloudStoreAccount): EekBackupStatus {
    return emergencyExitKitRepository
      .read(cloudAccount)
      .fold(
        success = {
          logInfo { "EEK backup status check: backup is healthy" }
          EekBackupStatus.Healthy(
            // TODO(BKR-1154): use actual timestamp from backup
            lastUploaded = Clock.System.now()
          )
        },
        failure = {
          // TODO(BKR-1153): handle unknown loading errors
          logInfo { "EEK backup status check: backup missing or unreadable" }
          EekBackupStatus.ProblemWithBackup.BackupMissing
        }
      )
  }

}
