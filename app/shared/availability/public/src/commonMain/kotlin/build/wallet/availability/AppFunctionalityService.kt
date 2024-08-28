package build.wallet.availability

import kotlinx.coroutines.flow.StateFlow

/**
 * Domain service for providing [AppFunctionalityStatus] based on network, f8e
 * reachability and other factors.
 */
interface AppFunctionalityService {
  /**
   * Emits a flow of the current [AppFunctionalityStatus], driven by changes in
   * network reachability conditions from [NetworkReachabilityProvider].
   *
   * The status is synced by the [AppFunctionalitySyncWorker].
   */
  val status: StateFlow<AppFunctionalityStatus>
}
