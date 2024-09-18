package build.wallet.feature

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.AppSessionManager
import build.wallet.analytics.events.AppSessionState
import build.wallet.bitkey.account.Account
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.featureflags.F8eFeatureFlagValue
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient.F8eFeatureFlag
import build.wallet.isOk
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class FeatureFlagSyncerImpl(
  private val accountService: AccountService,
  private val debugOptionsService: DebugOptionsService,
  private val featureFlagsF8eClient: FeatureFlagsF8eClient,
  private val clock: Clock,
  private val remoteFlags: List<FeatureFlag<out FeatureFlagValue>>,
  private val appSessionManager: AppSessionManager,
) : FeatureFlagSyncer {
  /**
   * A lock to ensure that only one sync is performed at a time.
   */
  private val syncLock = Mutex()

  /**
   * Time since the last sync. Will not sync more often then every 5 seconds.
   */
  private var lastSyncTime: Instant? = null

  override suspend fun initializeSyncLoop(scope: CoroutineScope) {
    appSessionManager.appSessionState
      .onEach {
        if (it == AppSessionState.FOREGROUND && scope.isActive) {
          performSync(SyncRequest.ApplicationDidEnterForegroundSyncRequest)
        }
      }
      .launchIn(scope)
  }

  override suspend fun sync() {
    performSync(SyncRequest.DemandSync)
  }

  /**
   * Perform a sync of remote feature flags.
   * @param syncRequest if the sync was initiated from foregrounding the application.
   * - If true, will not sync at a frequency of more than every 5 seconds, and will not synchronize
   * until the sync from the application launch has completed once.
   */
  private suspend fun performSync(syncRequest: SyncRequest) {
    syncLock.withLock {
      if (syncRequest == SyncRequest.ApplicationDidEnterForegroundSyncRequest && !canSync()) {
        return
      }

      val account = accountService.accountStatus().first().get()?.let {
        when (it) {
          is AccountStatus.ActiveAccount -> it.account
          is AccountStatus.LiteAccountUpgradingToFullAccount -> it.account
          AccountStatus.NoAccount -> null
          is AccountStatus.OnboardingAccount -> it.account
        }
      }

      val accountId = account?.accountId

      val f8eEnvironment = account.getF8eEnvironment()

      featureFlagsF8eClient.getF8eFeatureFlags(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        flagKeys = remoteFlags.map { it.identifier }
      )
        .onSuccess { remoteFlags ->
          remoteFlags.forEach { remoteFlag ->
            updateLocalFlagValue(remoteFlag)
          }
        }
        .onFailure {
          // The app encountered a network error when fetching remote feature flags from f8e. By
          // design, do nothing in this case and leave the local flags as they were.
        }
        .logNetworkFailure { "Failed to get feature flags from f8e" }

      lastSyncTime = clock.now()
    }
  }

  private suspend fun updateLocalFlagValue(remoteFlag: F8eFeatureFlag) {
    val matchingFlags = remoteFlags
      .filter { it.identifier == remoteFlag.key }
      .filter { !it.isOverridden() }

    when (val remoteFeatureFlagValue = remoteFlag.value) {
      is F8eFeatureFlagValue.BooleanValue -> {
        val flagValue = FeatureFlagValue.BooleanFlag(remoteFeatureFlagValue.value)
        matchingFlags
          .map { it as FeatureFlag<FeatureFlagValue.BooleanFlag> }
          .filter {
            it.canSetValue(flagValue).isOk()
          }
          .forEach {
            it.setFlagValue(flagValue)
          }
      }

      is F8eFeatureFlagValue.DoubleValue -> {
        val flagValue = FeatureFlagValue.DoubleFlag(remoteFeatureFlagValue.value)
        matchingFlags
          .map { it as FeatureFlag<FeatureFlagValue.DoubleFlag> }
          .filter {
            it.canSetValue(flagValue).isOk()
          }
          .forEach {
            it.setFlagValue(flagValue)
          }
      }
      is F8eFeatureFlagValue.StringValue -> {
        val flagValue = FeatureFlagValue.StringFlag(remoteFeatureFlagValue.value)
        matchingFlags
          .map { it as FeatureFlag<FeatureFlagValue.StringFlag> }
          .filter {
            it.canSetValue(flagValue).isOk()
          }
          .forEach {
            it.setFlagValue(flagValue)
          }
      }
    }
  }

  private fun canSync(): Boolean {
    val lastSync = lastSyncTime
    return (lastSync != null && clock.now() - lastSync > 5.seconds)
  }

  private suspend fun Account?.getF8eEnvironment(): F8eEnvironment {
    return this?.config?.f8eEnvironment ?: debugOptionsService.options().first().f8eEnvironment
  }

  private sealed interface SyncRequest {
    data object DemandSync : SyncRequest

    data object ApplicationDidEnterForegroundSyncRequest : SyncRequest
  }
}
