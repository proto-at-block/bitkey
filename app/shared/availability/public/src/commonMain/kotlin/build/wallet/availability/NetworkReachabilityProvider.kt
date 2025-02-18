package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import io.ktor.client.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider of the [NetworkReachability] of connections in the app.
 *
 * Maintains in-memory flows that default to [REACHABLE] and are updated when a
 * [NetworkConnection] first fails.
 */
interface NetworkReachabilityProvider {
  /**
   * Flow of the [NetworkReachability] for the overall internet connection in the app.
   */
  fun internetReachabilityFlow(): StateFlow<NetworkReachability>

  /**
   * Flow of the [NetworkReachability] for the network connection to F8e (Block's server).
   * Note: can be [UNREACHABLE] when [internetReachabilityFlow] is [REACHABLE].
   */
  fun f8eReachabilityFlow(environment: F8eEnvironment): StateFlow<NetworkReachability>

  /**
   * Updates the [NetworkReachability] for the given [NetworkConnection]
   */
  suspend fun updateNetworkReachabilityForConnection(
    httpClient: HttpClient? = null,
    reachability: NetworkReachability,
    connection: NetworkConnection,
  )
}
