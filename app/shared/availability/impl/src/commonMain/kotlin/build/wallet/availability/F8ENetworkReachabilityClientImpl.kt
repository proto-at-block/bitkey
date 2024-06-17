package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.OfflineOperationException
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.f8e.url
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.catching
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.head

class F8ENetworkReachabilityClientImpl(
  private val unauthenticatedF8eHttpClient: UnauthenticatedF8eHttpClient,
) : F8eNetworkReachabilityClient {
  override suspend fun checkConnection(f8eEnvironment: F8eEnvironment): Result<Unit, HttpError> {
    return when (f8eEnvironment) {
      F8eEnvironment.ForceOffline -> Err(HttpError.NetworkError(OfflineOperationException))
      else -> unauthenticatedF8eHttpClient.unauthenticated(f8eEnvironment).catching {
        head(f8eEnvironment.url)
      }.map {}
    }
  }
}
