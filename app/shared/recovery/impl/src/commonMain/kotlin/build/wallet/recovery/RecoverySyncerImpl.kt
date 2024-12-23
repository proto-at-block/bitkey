package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClient
import build.wallet.platform.app.AppSessionManager
import build.wallet.recovery.RecoverySyncer.SyncError
import build.wallet.recovery.RecoverySyncer.SyncError.CouldNotFetchServerRecovery
import build.wallet.recovery.RecoverySyncer.SyncError.SyncDbError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
class RecoverySyncerImpl(
  val recoveryDao: RecoveryDao,
  val getRecoveryStatusF8eClient: GetDelayNotifyRecoveryStatusF8eClient,
  val appSessionManager: AppSessionManager,
) : RecoverySyncer {
  /**
   * A mutex used to ensure only one call to sync is in flight at a time
   * and to record unusually long syncs.
   */
  private val syncLock = Mutex(locked = false)

  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ) {
    scope.launch {
      while (isActive) {
        if (appSessionManager.isAppForegrounded()) {
          performSync(
            fullAccountId = fullAccountId,
            f8eEnvironment = f8eEnvironment
          )
        }

        delay(syncFrequency)
      }
    }
  }

  override suspend fun performSync(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, SyncError> =
    coroutineBinding {
      syncLock.withLock {
        val serverRecovery =
          getRecoveryStatusF8eClient.getStatus(f8eEnvironment, fullAccountId)
            .mapError { CouldNotFetchServerRecovery(it) }
            .bind()

        recoveryDao
          .setActiveServerRecovery(serverRecovery)
          .mapError { SyncDbError(it) }
          .bind()
      }
    }

  override fun recoveryStatus(): Flow<Result<Recovery, Error>> {
    // Return a flow that emits whenever the recovery status changes. This could be an advancement
    // of a local recovery to a new phase, or entering a state where our local recovery attempt
    // was canceled on the server. We do this by listening to changes to both the cached active
    // serer recovery and any local ongoing recovery attempt we have and running comparisons
    // against the two to calculate the recovery status.
    return recoveryDao.activeRecovery().distinctUntilChanged()
  }

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, Error> {
    return recoveryDao.setLocalRecoveryProgress(progress)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return recoveryDao.clear()
  }
}
