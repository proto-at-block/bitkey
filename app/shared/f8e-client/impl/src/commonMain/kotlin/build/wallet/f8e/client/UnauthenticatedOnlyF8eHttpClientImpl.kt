package build.wallet.f8e.client

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.url
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType

/**
 * @property networkReachabilityProvider: If provided, all requests using this HttpClient
 * will report the status of the response back to the provider.
 */
class UnauthenticatedOnlyF8eHttpClientImpl(
  private val f8eHttpClientProvider: F8eHttpClientProvider,
  private val networkReachabilityProvider: NetworkReachabilityProvider?,
) : UnauthenticatedF8eHttpClient {
  override suspend fun unauthenticated(
    f8eEnvironment: F8eEnvironment,
    engine: HttpClientEngine?,
  ): HttpClient =
    f8eHttpClientProvider.getHttpClient(engine = engine) {
      f8eHttpClientProvider.configureCommon(this, null)
      defaultRequest {
        accept(Application.Json)
        contentType(Application.Json)
        url(f8eEnvironment.url)
      }
      networkReachabilityProvider?.let {
        HttpResponseValidator {
          networkReachabilityPlugin(
            connection = NetworkConnection.HttpClientNetworkConnection.F8e(f8eEnvironment),
            networkReachabilityProvider = networkReachabilityProvider
          )
        }
      }
    }.interceptWhenOffline(f8eEnvironment)
}
