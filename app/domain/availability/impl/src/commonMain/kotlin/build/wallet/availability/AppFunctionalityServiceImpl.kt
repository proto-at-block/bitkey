package build.wallet.availability

import bitkey.account.AccountConfigService
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.NetworkConnection.ElectrumSyncerNetworkConnection
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Emergency
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class AppFunctionalityServiceImpl(
  private val accountConfigService: AccountConfigService,
  private val networkReachabilityEventDao: NetworkReachabilityEventDao,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider,
  private val appVariant: AppVariant,
) : AppFunctionalityService, AppFunctionalitySyncWorker {
  private val defaultStatus = when (appVariant) {
    // Immediately assume emergency mode if the app variant is emergency
    Emergency -> LimitedFunctionality(EmergencyExitMode)
    // Otherwise, assume full functionality
    else -> FullFunctionality
  }
  private val statusCache = MutableStateFlow(defaultStatus)

  override val status: StateFlow<AppFunctionalityStatus> = statusCache.asStateFlow()

  override suspend fun executeWork() {
    accountConfigService.activeOrDefaultConfig()
      .map { it.f8eEnvironment }
      .flatMapLatest(::status)
      .collectLatest(statusCache::emit)
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
          cause = EmergencyExitMode
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
