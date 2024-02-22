package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.flow.Flow

/**
 * A provider for the current overall functionality status of the app, either
 * normal, full status or limited status in the case of degraded network
 * reachability conditions.
 */
interface AppFunctionalityStatusProvider {
  /**
   * Emits a flow of the current [AppFunctionalityStatus], driven by changes in
   * network reachability conditions from [NetworkReachabilityProvider].
   */
  fun appFunctionalityStatus(f8eEnvironment: F8eEnvironment): Flow<AppFunctionalityStatus>
}
