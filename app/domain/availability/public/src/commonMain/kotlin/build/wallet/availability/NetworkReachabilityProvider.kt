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
   * Synchronously checks if the device has a working internet connection.
   *
   * Platform behavior:
   * - **Android**: Checks `NET_CAPABILITY_VALIDATED` and `NET_CAPABILITY_INTERNET`
   *   on the active network. This addresses the Android-specific issue where the
   *   OS reports "connected" before the network is validated and DNS works.
   * - **iOS**: Checks NWPathMonitor path status.
   * - **JVM**: Returns true (tests/desktop don't have mobile networking edge cases).
   *
   * This is a fast check suitable for use in request interceptors.
   */
  fun hasInternetConnection(): Boolean

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
