package build.wallet.availability

import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.ktor.result.isClientError
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Extension on [HttpCallValidator.Config] that sets up handlers for HttpClient responses
 * and exceptions in order to update the given [NetworkReachabilityProvider].
 */
fun HttpCallValidator.Config.networkReachabilityPlugin(
  connection: NetworkConnection.HttpClientNetworkConnection,
  networkReachabilityProvider: NetworkReachabilityProvider,
) {
  // Set up a callback to update the [NetworkReachabilityProvider] based on responses
  validateResponse { response ->
    val responseStatus = response.status
    networkReachabilityProvider.updateNetworkReachabilityForConnection(
      reachability =
        when {
          // REACHABLE if success
          responseStatus.isSuccess() -> REACHABLE
          // UNREACHABLE if forbidden (403)
          responseStatus == HttpStatusCode.Forbidden -> UNREACHABLE
          // REACHABLE if client error (other 4xx)
          responseStatus.isClientError -> REACHABLE
          // UNREACHABLE if any other error
          else -> UNREACHABLE
        },
      connection = connection
    )
  }

  // Set up a callback to update the [NetworkReachabilityProvider] based on exceptions
  handleResponseExceptionWithRequest { _, _ ->
    // Any exception is treated as the connection being unreachable
    networkReachabilityProvider.updateNetworkReachabilityForConnection(
      reachability = UNREACHABLE,
      connection = connection
    )
  }
}
