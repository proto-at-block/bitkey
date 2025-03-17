package build.wallet.f8e.analytics

import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface EventTrackerF8eClient {
  /**
   * Sends analytics events to analytics service
   */
  suspend fun trackEvent(
    f8eEnvironment: F8eEnvironment,
    eventBundle: EventBundle,
  ): Result<Unit, NetworkingError>
}
