package build.wallet.availability

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkReachabilityProviderMock(
  turbine: (String) -> Turbine<Any>,
) : NetworkReachabilityProvider {
  val updateNetworkReachabilityForConnectionCalls =
    turbine("updateNetworkReachabilityForConnection calls")

  private val internetReachability = MutableStateFlow(NetworkReachability.REACHABLE)
  private val f8eReachability = MutableStateFlow(NetworkReachability.UNREACHABLE)

  data class UpdateNetworkReachabilityForConnectionParams(
    val reachability: NetworkReachability,
    val connection: NetworkConnection,
  )

  override fun internetReachabilityFlow(): StateFlow<NetworkReachability> {
    return internetReachability
  }

  override fun f8eReachabilityFlow(environment: F8eEnvironment): StateFlow<NetworkReachability> {
    return f8eReachability
  }

  override suspend fun updateNetworkReachabilityForConnection(
    reachability: NetworkReachability,
    connection: NetworkConnection,
  ) {
    when (connection) {
      is NetworkConnection.HttpClientNetworkConnection.F8e -> f8eReachability.value = reachability
      else -> {} // no-op
    }
    updateNetworkReachabilityForConnectionCalls.add(
      UpdateNetworkReachabilityForConnectionParams(reachability, connection)
    )
  }
}
