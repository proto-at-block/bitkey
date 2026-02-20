package bitkey.recovery

import bitkey.account.AccountConfigService
import bitkey.recovery.RecoveryStatusService.SyncError.CouldNotFetchServerRecovery
import bitkey.recovery.RecoveryStatusService.SyncError.SyncDbError
import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.coroutines.flow.TickerFlowFactory
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
  private val tickerFlowFactory: TickerFlowFactory,
) : RecoveryStatusService, RecoverySyncWorker {
  private val _status = MutableStateFlow<Recovery>(Recovery.Loading)

  override val status: StateFlow<Recovery> = _status

  /**
   * Signal to trigger an immediate sync when local recovery is initiated.
   * This ensures deterministic behavior when [setLocalRecoveryProgress] is called.
   */
  private val syncTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  override suspend fun executeWork() {
    coroutineScope {
      val activeAccount = accountService.activeAccount().distinctUntilChanged()
      val activeRecovery = recoveryDao.activeRecovery()
        .mapNotNull { it.get() }
        .distinctUntilChanged()

      // Sync recovery:
      // - when we have an active full account (with Lost Hardware recovery or in case if there's a Recovery Conflict)
      // - when we don't have an active full account but have active recovery (Lost App + Cloud recovery)
      combine(activeAccount, activeRecovery) { account, recovery ->
        _status.update { recovery }

        val hasFullAccount = account is FullAccount
        // Specifically ignore Lost Hardware recovery because it should only happen when we already
        // have a full account. This prevents any possibility for race conditions between account
        // and recovery flows.
        val hasActiveLostAppRecovery =
          (recovery as? ServerDependentRecovery)?.factorToRecover == App
        when {
          // If we have an active full account, sync using that account's ID
          // and mark that we have an active account (for SomeoneElseIsRecovering detection)
          hasFullAccount -> SyncContext(account.accountId, hasActiveAccount = true)
          // If we're doing Lost App recovery (no active account), sync using the recovery's account ID
          // and mark that we don't have an active account (for race condition prevention)
          hasActiveLostAppRecovery -> SyncContext(recovery.fullAccountId, hasActiveAccount = false)
          else -> null
        }
      }
        .distinctUntilChanged()
        .flatMapLatest { syncContext ->
          syncContext?.let {
            // Merge periodic ticker with manual sync trigger for immediate sync when local recovery is initiated
            merge(
              tickerFlowFactory.create(recoverySyncFrequency.value),
              syncTrigger
            )
              .map { syncContext }
              .filter { appSessionManager.isAppForegrounded() }
          } ?: emptyFlow()
        }
        .collectLatest { syncContext ->
          performSync(syncContext.accountId, syncContext.hasActiveAccount)
        }
    }
  }

  /**
   * Context for sync operations, containing the account ID and whether we have an active account.
   * This is used to determine whether to check for local recovery presence (race condition prevention)
   * or to always set server recovery (legitimate SomeoneElseIsRecovering detection).
   */
  private data class SyncContext(
    val accountId: FullAccountId,
    val hasActiveAccount: Boolean,
  )

  /**
   * Unary sync request. Gets the server's active recovery, caches it, and returns a [Recovery]
   * which indicates, if there is an active server recovery, whether it matches any active recovery
   * that the client is attempting.
   *
   * @param accountId The account ID to sync recovery status for.
   * @param hasActiveAccount Whether we have an active full account. If true, we always set server
   *   recovery to enable SomeoneElseIsRecovering detection. If false (Lost App recovery case),
   *   we check for local recovery presence to prevent race conditions.
   */
  private suspend fun performSync(
    accountId: FullAccountId,
    hasActiveAccount: Boolean,
  ): Result<Unit, RecoveryStatusService.SyncError> =
    coroutineBinding {
      recoveryLock.withLock {
        val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
        val serverRecovery =
          getRecoveryStatusF8eClient.getStatus(f8eEnvironment, accountId)
            .mapError { CouldNotFetchServerRecovery(it) }
            .bind()

        /**
         * Race condition prevention for Lost App recovery:
         *
         * When initiating Lost App recovery (no active account), there's a potential race condition:
         * 1. User initiates recovery (server is notified)
         * 2. Sync worker fetches server recovery status
         * 3. Server recovery is saved to DB BEFORE local recovery is initiated
         * 4. User incorrectly sees "SomeoneElseIsRecovering"
         *
         * To prevent this, when syncing during Lost App recovery (hasActiveAccount = false),
         * we check for local recovery presence. If local recovery is not yet present,
         * we skip setting server recovery. When local recovery is initiated via
         * [setLocalRecoveryProgress], the [activeRecovery] flow will emit a new value,
         * which will trigger another sync attempt.
         *
         * However, if we have an active account (hasActiveAccount = true), we always set
         * server recovery to enable legitimate SomeoneElseIsRecovering detection - where
         * someone else is trying to recover the account from a different device.
         */
        if (serverRecovery != null && !hasActiveAccount) {
          val localRecoveryPresent = recoveryDao.isLocalRecoveryPresent()
            .mapError { SyncDbError(it) }.bind()
          if (!localRecoveryPresent) {
            return@coroutineBinding
          }
        }
        recoveryDao
          .setActiveServerRecovery(serverRecovery)
          .mapError { SyncDbError(it) }
          .bind()
      }
    }

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, Error> {
    return recoveryDao.setLocalRecoveryProgress(progress).also {
      // Trigger an immediate sync when local recovery is first initiated
      // to ensure server recovery is set now that local recovery is present
      if (progress is LocalRecoveryAttemptProgress.CreatedPendingKeybundles) {
        syncTrigger.tryEmit(Unit)
      }
    }
  }

  override suspend fun clear(): Result<Unit, Error> {
    return recoveryDao.clear()
  }
}
