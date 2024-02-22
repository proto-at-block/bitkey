package build.wallet.availability

import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Result

/**
 * Service for checking the reachability of internet
 */
interface InternetNetworkReachabilityService {
  /**
   * Performs a simple header request to check if an internet connection can be formed.
   */
  suspend fun checkConnection(): Result<Unit, HttpError>
}
