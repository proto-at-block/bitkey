package build.wallet.cloud.backup.health

import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitRepository
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class CloudBackupHealthRepositoryImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyExitKitRepository: EmergencyExitKitRepository,
  private val fullAccountCloudBackupRepairer: FullAccountCloudBackupRepairer,
  private val appFunctionalityService: AppFunctionalityService,
) : CloudBackupHealthRepository {
  private val appKeyBackupStatus = MutableStateFlow<AppKeyBackupStatus?>(null)

  override fun appKeyBackupStatus(): StateFlow<AppKeyBackupStatus?> {
    return appKeyBackupStatus
  }

  private val eekBackupStatus = MutableStateFlow<EekBackupStatus?>(null)

  override fun eekBackupStatus(): StateFlow<EekBackupStatus?> {
    return eekBackupStatus
  }

  /**
   * A lock to ensure that only one sync is performed at a time.
   */
  private val syncLock = Mutex()

  override suspend fun performSync(account: FullAccount): CloudBackupStatus {
    return syncLock.withLock {
      getCurrentCloudAccount()
        .fold(
          success = { cloudAccount ->
            val cloudBackupStatus = syncBackupStatus(cloudAccount, account)

            if (cloudBackupStatus.isHealthy()) {
              cloudBackupStatus
            } else {
              // Attempt to repair the backup silently first
              fullAccountCloudBackupRepairer.attemptRepair(account, cloudAccount, cloudBackupStatus)
              // Re-sync and return whatever the status is after repair attempt.
              syncBackupStatus(cloudAccount, account)
            }
          },
          failure = {
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
      .toErrorIfNull { CloudStoreAccountMissingError }
  }

  private object CloudStoreAccountMissingError : Error()

  private suspend fun syncBackupStatus(
    cloudAccount: CloudStoreAccount,
    account: FullAccount,
  ) = CloudBackupStatus(
    appKeyBackupStatus = syncAppKeyBackupStatus(cloudAccount, account),
    eekBackupStatus = syncEekBackupStatus(cloudAccount)
  )

  private suspend fun syncAppKeyBackupStatus(
    cloudAccount: CloudStoreAccount,
    account: FullAccount,
  ): AppKeyBackupStatus {
    if (appFunctionalityService.status.value.featureStates.cloudBackupHealth == Unavailable) {
      return AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
    }

    val localCloudBackup = cloudBackupDao
      .get(account.accountId.serverId)
      .toErrorIfNull { Error("No local backup found") }
      .logFailure { "Error finding local backup" }
      .get()
      // We are missing a local backup, so we can't validate the integrity of the cloud backup.
      // Mark backup as missing to let the customer
      ?: return AppKeyBackupStatus.ProblemWithBackup.BackupMissing

    return cloudBackupRepository
      .readActiveBackup(cloudAccount)
      .fold(
        success = { cloudBackup ->
          when (cloudBackup) {
            null -> AppKeyBackupStatus.ProblemWithBackup.BackupMissing
            else -> {
              if (cloudBackup != localCloudBackup) {
                logWarn { "Cloud backup does not match local backup" }
                AppKeyBackupStatus.ProblemWithBackup.InvalidBackup(cloudBackup)
              } else {
                // TODO(BKR-1155): do we need to perform additional integrity checks?
                AppKeyBackupStatus.Healthy(
                  // TODO(BKR-1154): use actual timestamp from backup
                  lastUploaded = Clock.System.now()
                )
              }
            }
          }
        },
        failure = {
          // TODO(BKR-1156): handle unknown loading errors
          logWarn { "Failed to read cloud backup during sync: $it" }
          AppKeyBackupStatus.ProblemWithBackup.NoCloudAccess
        }
      )
  }

  private suspend fun syncEekBackupStatus(cloudAccount: CloudStoreAccount): EekBackupStatus {
    return emergencyExitKitRepository
      .read(cloudAccount)
      .fold(
        success = {
          EekBackupStatus.Healthy(
            // TODO(BKR-1154): use actual timestamp from backup
            lastUploaded = Clock.System.now()
          )
        },
        failure = {
          // TODO(BKR-1153): handle unknown loading errors
          EekBackupStatus.ProblemWithBackup.BackupMissing
        }
      )
  }
}
