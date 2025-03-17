package build.wallet.f8e.client.plugins

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.ktor.result.isClientError
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Extension on [HttpCallValidator.Config] that sets up handlers for HttpClient responses
 * and exceptions in order to update the given [NetworkReachabilityProvider].
 */
fun HttpCallValidator.Config.networkReachabilityPlugin(
  connection: NetworkConnection.HttpClientNetworkConnection?,
  networkReachabilityProvider: NetworkReachabilityProvider,
) {
  // Set up a callback to update the [NetworkReachabilityProvider] based on responses
  validateResponse { response ->
    if (response.call.attributes.getOrNull(CheckReachabilityAttribute) != true) {
      return@validateResponse
    }
    val responseStatus = response.status
    networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = response.call.client,
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
        ?: NetworkConnection.HttpClientNetworkConnection.F8e(
          environment = response.call.attributes[F8eEnvironmentAttribute]
        )
    )
  }

  // Set up a callback to update the [NetworkReachabilityProvider] based on exceptions
  handleResponseExceptionWithRequest { exception, request ->
    try {
      if (request.call.attributes.getOrNull(CheckReachabilityAttribute) != true) {
        return@handleResponseExceptionWithRequest
      }
    } catch (_: IllegalStateException) {
      // ignore errors for usage with the ForceOfflinePlugin
      // which prevents `response.call` from being initialized
    }
    val client = (exception as? ResponseException)?.response?.call?.client

    // Any exception is treated as the connection being unreachable
    networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = client,
      reachability = UNREACHABLE,
      connection = connection
        ?: NetworkConnection.HttpClientNetworkConnection.F8e(
          environment = request.attributes[F8eEnvironmentAttribute]
        )
    )
  }
}
