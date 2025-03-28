package bitkey.recovery

import bitkey.account.AccountConfigService
import bitkey.recovery.RecoveryStatusService.SyncError.CouldNotFetchServerRecovery
import bitkey.recovery.RecoveryStatusService.SyncError.SyncDbError
import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClient
import build.wallet.platform.app.AppSessionManager
import build.wallet.recovery.*
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class RecoveryStatusServiceImpl(
  private val accountService: AccountService,
  private val recoveryDao: RecoveryDao,
  private val getRecoveryStatusF8eClient: GetDelayNotifyRecoveryStatusF8eClient,
  private val appSessionManager: AppSessionManager,
  private val recoveryLock: RecoveryLock,
  private val accountConfigService: AccountConfigService,
  private val recoverySyncFrequency: RecoverySyncFrequency,
) : RecoveryStatusService, RecoverySyncWorker {
  override suspend fun executeWork() {
    coroutineScope {
      val activeAccount = accountService.activeAccount().distinctUntilChanged()
      val activeRecovery = status().map { it.get() }.distinctUntilChanged()

      // Sync recovery:
      // - when we have an active full account (with Lost Hardware recovery or in case if there's a Recovery Conflict)
      // - when we don't have an active full account but have active recovery (Lost App + Cloud recovery)
      combine(activeAccount, activeRecovery) { account, recovery ->
        val hasFullAccount = account is FullAccount
        // Specifically ignore Lost Hardware recovery because it should only happen when we already
        // have a full account. This prevents any possibility for race conditions between account
        // and recovery flows.
        val hasActiveLostAppRecovery =
          (recovery as? ServerDependentRecovery)?.factorToRecover == App
        when {
          hasFullAccount -> account.accountId
          hasActiveLostAppRecovery -> recovery.fullAccountId
          else -> null
        }
      }.distinctUntilChanged()
        .collectLatest { accountId ->
          if (accountId != null) {
            launchTicker(recoverySyncFrequency.value) {
              if (appSessionManager.isAppForegrounded()) {
                performSync(accountId)
              }
            }
          }
        }
    }
  }

  /**
   * Unary sync request. Gets the server's active recovery, caches it, and returns a [Recovery]
   * which indicates, if there is an active server recovery, whether it matches any active recovery
   * that the client is attempting.
   */
  private suspend fun performSync(
    accountId: FullAccountId,
  ): Result<Unit, RecoveryStatusService.SyncError> =
    coroutineBinding {
      recoveryLock.withLock {
        val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
        val serverRecovery =
          getRecoveryStatusF8eClient.getStatus(f8eEnvironment, accountId)
            .mapError { CouldNotFetchServerRecovery(it) }
            .bind()

        recoveryDao
          .setActiveServerRecovery(serverRecovery)
          .mapError { SyncDbError(it) }
          .bind()
      }
    }

  override fun status(): Flow<Result<Recovery, Error>> {
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
