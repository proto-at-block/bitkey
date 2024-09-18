package build.wallet.availability

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.NetworkConnection.ElectrumSyncerNetworkConnection
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.bitkey.account.Account
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.F8eEnvironment
import build.wallet.mapResult
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Emergency
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.*

class AppFunctionalityServiceImpl(
  private val accountService: AccountService,
  private val debugOptionsService: DebugOptionsService,
  private val networkReachabilityEventDao: NetworkReachabilityEventDao,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider,
  private val appVariant: AppVariant,
) : AppFunctionalityService, AppFunctionalitySyncWorker {
  private val defaultStatus = when (appVariant) {
    // Immediately assume emergency mode if the app variant is emergency
    Emergency -> LimitedFunctionality(EmergencyAccessMode)
    // Otherwise, assume full functionality
    else -> FullFunctionality
  }
  private val statusCache = MutableStateFlow(defaultStatus)

  override val status: StateFlow<AppFunctionalityStatus> = statusCache.asStateFlow()

  override suspend fun executeWork() {
    f8eEnvironment()
      .flatMapLatest(::status)
      .collectLatest(statusCache::emit)
  }

  private fun f8eEnvironment(): Flow<F8eEnvironment> {
    return combine(
      account(),
      debugOptionsService.options()
    ) { account, debugOptions ->
      account?.config?.f8eEnvironment ?: debugOptions.f8eEnvironment
    }.distinctUntilChanged()
  }

  private fun account(): Flow<Account?> {
    return accountService.accountStatus()
      .mapResult { status ->
        when (status) {
          is AccountStatus.ActiveAccount -> status.account
          is AccountStatus.LiteAccountUpgradingToFullAccount -> status.account
          is AccountStatus.OnboardingAccount -> status.account
          else -> null
        }
      }
      .map { it.get() }
      .distinctUntilChanged()
  }

  private fun status(f8eEnvironment: F8eEnvironment): Flow<AppFunctionalityStatus> {
    return combine(
      networkReachabilityProvider.f8eReachabilityFlow(f8eEnvironment),
      networkReachabilityProvider.internetReachabilityFlow(),
      f8eAuthSignatureStatusProvider.authSignatureStatus()
    ) { f8eReachability, internetReachability, authSignatureStatus ->
      when {
        internetReachability == UNREACHABLE -> LimitedFunctionality(
          cause = InternetUnreachable(
            lastReachableTime = networkReachabilityEventDao
              .getMostRecentReachableEvent(null).get(),
            lastElectrumSyncReachableTime = networkReachabilityEventDao
              .getMostRecentReachableEvent(ElectrumSyncerNetworkConnection).get()
          )
        )
        authSignatureStatus == Unauthenticated -> LimitedFunctionality(
          cause = InactiveApp
        )
        appVariant == Emergency -> LimitedFunctionality(
          cause = EmergencyAccessMode
        )
        f8eReachability == UNREACHABLE -> LimitedFunctionality(
          cause = F8eUnreachable(
            lastReachableTime = networkReachabilityEventDao
              .getMostRecentReachableEvent(F8e(f8eEnvironment)).get()
          )
        )
        else -> FullFunctionality
      }
    }.distinctUntilChanged()
  }
}
