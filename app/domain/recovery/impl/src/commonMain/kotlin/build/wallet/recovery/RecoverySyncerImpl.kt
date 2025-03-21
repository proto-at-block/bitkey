package build.wallet.recovery

import bitkey.account.AccountConfigService
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClient
import build.wallet.platform.app.AppSessionManager
import build.wallet.recovery.RecoverySyncer.SyncError
import build.wallet.recovery.RecoverySyncer.SyncError.CouldNotFetchServerRecovery
import build.wallet.recovery.RecoverySyncer.SyncError.SyncDbError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
class RecoverySyncerImpl(
  val recoveryDao: RecoveryDao,
  val getRecoveryStatusF8eClient: GetDelayNotifyRecoveryStatusF8eClient,
  val appSessionManager: AppSessionManager,
  private val recoveryLock: RecoveryLock,
  private val accountConfigService: AccountConfigService,
) : RecoverySyncer {
  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
    fullAccountId: FullAccountId,
  ) {
    scope.launchTicker(syncFrequency) {
      if (appSessionManager.isAppForegrounded()) {
        performSync(fullAccountId)
      }
    }
  }

  override suspend fun performSync(fullAccountId: FullAccountId): Result<Unit, SyncError> =
    coroutineBinding {
      recoveryLock.withLock {
        val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
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
