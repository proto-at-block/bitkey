package build.wallet.cloud.backup.health

import build.wallet.LoadableValue
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.coroutines.callCoroutineEvery
import build.wallet.emergencyaccesskit.EmergencyAccessKitRepository
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

class CloudBackupHealthRepositoryImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
  private val cloudBackupDao: CloudBackupDao,
  private val emergencyAccessKitRepository: EmergencyAccessKitRepository,
  private val fullAccountCloudBackupRepairer: FullAccountCloudBackupRepairer,
) : CloudBackupHealthRepository {
  private val mobileKeyBackupStatus =
    MutableStateFlow<LoadableValue<MobileKeyBackupStatus>>(LoadableValue.InitialLoading)

  override fun mobileKeyBackupStatus(): StateFlow<LoadableValue<MobileKeyBackupStatus>> {
    return mobileKeyBackupStatus
  }

  private val eakBackupStatus =
    MutableStateFlow<LoadableValue<EakBackupStatus>>(LoadableValue.InitialLoading)

  override fun eakBackupStatus(): StateFlow<LoadableValue<EakBackupStatus>> {
    return eakBackupStatus
  }

  /**
   * A shared flow that emits a [FullAccount] every time a sync is requested.
   * [syncLoop] listens to this flow and performs a sync whenever it emits.
   */
  private val syncRequests = MutableSharedFlow<FullAccount>(
    extraBufferCapacity = 1,
    onBufferOverflow = DROP_OLDEST
  )

  /**
   * A lock to ensure that only one sync is performed at a time.
   */
  private val syncLock = Mutex()

  override suspend fun syncLoop(
    scope: CoroutineScope,
    account: FullAccount,
  ) {
    scope.launch {
      // Perform sync whenever a sync is requested
      syncRequests
        // Only perform sync for the requested account as a safety measure.
        .filter { it == account }
        .collect {
          performSync(account)
        }
    }

    scope.launch {
      callCoroutineEvery(frequency = 24.hours) {
        // send sync signal to request a sync
        syncRequests.emit(account)
      }
    }
  }

  override fun requestSync(account: FullAccount) {
    syncRequests.tryEmit(account)
  }

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
              mobileKeyBackupStatus = MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess,
              eakBackupStatus = EakBackupStatus.ProblemWithBackup.NoCloudAccess
            )
          }
        )
        .also {
          mobileKeyBackupStatus.value = LoadableValue.LoadedValue(it.mobileKeyBackupStatus)
          eakBackupStatus.value = LoadableValue.LoadedValue(it.eakBackupStatus)
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
    mobileKeyBackupStatus = syncMobileKeyBackupStatus(cloudAccount, account),
    eakBackupStatus = syncEakBackupStatus(cloudAccount)
  )

  private suspend fun syncMobileKeyBackupStatus(
    cloudAccount: CloudStoreAccount,
    account: FullAccount,
  ): MobileKeyBackupStatus {
    val localCloudBackup = cloudBackupDao
      .get(account.accountId.serverId)
      .toErrorIfNull { Error("No local backup found") }
      .logFailure { "Error finding local backup" }
      .get()
      // We are missing a local backup, so we can't validate the integrity of the cloud backup.
      // Mark backup as missing to let the customer
      ?: return MobileKeyBackupStatus.ProblemWithBackup.BackupMissing

    return cloudBackupRepository
      .readBackup(cloudAccount)
      .fold(
        success = { cloudBackup ->
          when (cloudBackup) {
            null -> MobileKeyBackupStatus.ProblemWithBackup.BackupMissing
            else -> {
              if (cloudBackup != localCloudBackup) {
                log { "Cloud backup does not match local backup" }
                MobileKeyBackupStatus.ProblemWithBackup.InvalidBackup(cloudBackup)
              } else {
                // TODO(BKR-1155): do we need to perform additional integrity checks?
                MobileKeyBackupStatus.Healthy(
                  // TODO(BKR-1154): use actual timestamp from backup
                  lastUploaded = Clock.System.now()
                )
              }
            }
          }
        },
        failure = {
          // TODO(BKR-1156): handle unknown loading errors
          MobileKeyBackupStatus.ProblemWithBackup.NoCloudAccess
        }
      )
  }

  private suspend fun syncEakBackupStatus(cloudAccount: CloudStoreAccount): EakBackupStatus {
    return emergencyAccessKitRepository
      .read(cloudAccount)
      .fold(
        success = {
          EakBackupStatus.Healthy(
            // TODO(BKR-1154): use actual timestamp from backup
            lastUploaded = Clock.System.now()
          )
        },
        failure = {
          // TODO(BKR-1153): handle unknown loading errors
          EakBackupStatus.ProblemWithBackup.BackupMissing
        }
      )
  }
}
