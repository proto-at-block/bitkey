package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Result
import io.ktor.client.*

/**
 * Service for checking the reachability of f8e
 */
interface F8eNetworkReachabilityService {
  /**
   * Performs a simple header request to the url of the given [F8eEnvironment]
   * to check if a connection can be formed to it.
   */
  suspend fun checkConnection(f8eEnvironment: F8eEnvironment): Result<Unit, HttpError>

  suspend fun checkConnection(
    httpClient: HttpClient,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, HttpError>
}
