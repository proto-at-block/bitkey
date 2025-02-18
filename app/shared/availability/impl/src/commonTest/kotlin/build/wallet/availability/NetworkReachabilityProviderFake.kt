package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkReachabilityProviderFake : NetworkReachabilityProvider {
  val internetReachabilityFlow = MutableStateFlow(NetworkReachability.REACHABLE)

  override fun internetReachabilityFlow(): StateFlow<NetworkReachability> {
    return internetReachabilityFlow
  }

  val f8eEnvironmentReachabilityFlow = MutableStateFlow(NetworkReachability.REACHABLE)

  override fun f8eReachabilityFlow(environment: F8eEnvironment): StateFlow<NetworkReachability> {
    return f8eEnvironmentReachabilityFlow
  }

  override suspend fun updateNetworkReachabilityForConnection(
    httpClient: HttpClient?,
    reachability: NetworkReachability,
    connection: NetworkConnection,
  ) {
    when (connection) {
      is NetworkConnection.HttpClientNetworkConnection.F8e ->
        f8eEnvironmentReachabilityFlow.value =
          reachability
      else -> Unit // noop
    }
  }

  fun reset() {
    internetReachabilityFlow.value = NetworkReachability.REACHABLE
    f8eEnvironmentReachabilityFlow.value = NetworkReachability.REACHABLE
  }
}
