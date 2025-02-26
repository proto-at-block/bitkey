package build.wallet.availability

import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@BitkeyInject(AppScope::class)
class NetworkReachabilityProviderImpl(
  private val f8eNetworkReachabilityService: F8eNetworkReachabilityService,
  private val internetNetworkReachabilityService: InternetNetworkReachabilityService,
  private val networkReachabilityEventDao: NetworkReachabilityEventDao,
) : NetworkReachabilityProvider {
  private val internetStatusMutableFlow = MutableStateFlow(REACHABLE)
  private val f8eStatusMutableFlowMap:
    MutableMap<F8eEnvironment, MutableStateFlow<NetworkReachability>> =
    mutableMapOf()

  /**
   * The last environment sent in [updateNetworkReachabilityForConnection] for
   * [NetworkConnection.HttpClientNetworkConnection.F8e].
   *
   * Stored in order to be able to update [f8eStatusMutableFlow] when other
   * connections send status updates.
   */
  private var f8eEnvironment: F8eEnvironment? = null

  override fun internetReachabilityFlow(): StateFlow<NetworkReachability> =
    internetStatusMutableFlow

  override fun f8eReachabilityFlow(environment: F8eEnvironment): StateFlow<NetworkReachability> {
    return f8eStatusMutableFlow(environment)
  }

  override suspend fun updateNetworkReachabilityForConnection(
    httpClient: HttpClient?,
    reachability: NetworkReachability,
    connection: NetworkConnection,
  ) {
    // Store the F8e environment
    if (connection is F8e) {
      f8eEnvironment = connection.environment
    }

    // Persist the event
    networkReachabilityEventDao.insertReachabilityEvent(connection, reachability)

    // Update the flows based on the status
    when (reachability) {
      REACHABLE ->
        updateFlowsForReachableConnection(httpClient, connection)

      UNREACHABLE -> {
        updateFlowsForUnreachableConnection(httpClient, connection)
        // TODO (W-5711): Set up polling for re-connection
      }
    }
  }

  private suspend fun updateFlowsForReachableConnection(
    httpClient: HttpClient?,
    connection: NetworkConnection,
  ) {
    if (connection is F8e) {
      // If the connection is F8e and it is reporting REACHABLE, immediately update the flow.
      f8eStatusMutableFlow(connection.environment).emit(REACHABLE)
    } else if (httpClient != null) {
      // If the connection is not F8e and the status of F8e is UNREACHABLE, do a general F8e
      // connection check (not endpoint specific) and update the flow based on that.
      f8eEnvironment?.let { environment ->
        if (f8eStatusMutableFlow(environment).value == UNREACHABLE) {
          f8eNetworkReachabilityService.checkConnection(httpClient, environment)
            .onSuccess { f8eStatusMutableFlow(environment).emit(REACHABLE) }
        }
      }
    }
    // Wait to check for f8e connectivity before updating reachability.
    // Prevents showing the "some features may not be available" banner when transitioning
    // to fully online.
    internetStatusMutableFlow.emit(REACHABLE)
  }

  private suspend fun updateFlowsForUnreachableConnection(
    httpClient: HttpClient?,
    connection: NetworkConnection,
  ) {
    // Update F8e flow if the connection is F8e
    if (connection is F8e && httpClient != null) {
      // First perform a general F8e connection check (not endpoint specific) to double-check that
      // there’s not an isolated incident with the specific endpoint that experienced a failure,
      // and update the reachability based on that check
      f8eNetworkReachabilityService.checkConnection(httpClient, connection.environment)
        .onSuccess { f8eStatusMutableFlow(connection.environment).emit(REACHABLE) }
        .onFailure { f8eStatusMutableFlow(connection.environment).emit(UNREACHABLE) }
    } else if (connection is F8e) {
      // This case should only be hit if the connection is F8e and the httpClient is null due to the
      // use of the ForceOfflinePlugin. In this case, we can't check the connection, so we just
      // update the flow to UNREACHABLE.
      f8eStatusMutableFlow(connection.environment).emit(UNREACHABLE)
    }

    // Update internet flow for any connection

    // First perform a general internet check to double-check that there’s not an isolated
    // incident with the given [NetworkConnection], and update the flow based on the outcome
    internetNetworkReachabilityService.checkConnection()
      .onSuccess { internetStatusMutableFlow.emit(REACHABLE) }
      .onFailure {
        internetStatusMutableFlow.emit(UNREACHABLE)
        // If the internet is unreachable, that means F8e is also unreachable, optimize by
        // updating both here.
        f8eEnvironment?.let { f8eStatusMutableFlow(it).emit(UNREACHABLE) }
      }
  }

  private fun f8eStatusMutableFlow(
    environment: F8eEnvironment,
  ): MutableStateFlow<NetworkReachability> {
    if (!f8eStatusMutableFlowMap.containsKey(environment)) {
      f8eStatusMutableFlowMap[environment] = MutableStateFlow(REACHABLE)
    }
    return f8eStatusMutableFlowMap[environment]!!
  }
}
