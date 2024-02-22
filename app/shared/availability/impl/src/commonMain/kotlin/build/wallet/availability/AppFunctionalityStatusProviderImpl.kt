package build.wallet.availability

import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.NetworkConnection.ElectrumSyncerNetworkConnection
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.config.AppVariant
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AppFunctionalityStatusProviderImpl(
  private val networkReachabilityEventDao: NetworkReachabilityEventDao,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider,
  private val appVariant: AppVariant,
) : AppFunctionalityStatusProvider {
  override fun appFunctionalityStatus(
    f8eEnvironment: F8eEnvironment,
  ): Flow<AppFunctionalityStatus> {
    return combine(
      networkReachabilityProvider.f8eReachabilityFlow(f8eEnvironment),
      networkReachabilityProvider.internetReachabilityFlow(),
      f8eAuthSignatureStatusProvider.authSignatureStatus()
    ) { f8eReachability, internetReachability, authSignatureStatus ->
      when {
        internetReachability == UNREACHABLE -> LimitedFunctionality(
          cause =
            InternetUnreachable(
              lastReachableTime =
                networkReachabilityEventDao
                  .getMostRecentReachableEvent(null).get(),
              lastElectrumSyncReachableTime =
                networkReachabilityEventDao
                  .getMostRecentReachableEvent(ElectrumSyncerNetworkConnection).get()
            )
        )
        authSignatureStatus == AuthSignatureStatus.Unauthenticated -> LimitedFunctionality(
          cause = InactiveApp
        )
        appVariant == AppVariant.Emergency -> LimitedFunctionality(
          cause = EmergencyAccessMode
        )
        f8eReachability == UNREACHABLE -> LimitedFunctionality(
          cause =
            F8eUnreachable(
              lastReachableTime =
                networkReachabilityEventDao
                  .getMostRecentReachableEvent(F8e(f8eEnvironment)).get()
            )
        )
        else -> AppFunctionalityStatus.FullFunctionality
      }
    }
  }
}
