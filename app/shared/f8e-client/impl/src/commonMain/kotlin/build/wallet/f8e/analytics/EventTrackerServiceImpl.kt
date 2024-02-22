package build.wallet.f8e.analytics

import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.client.contentType
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class EventTrackerServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : EventTrackerService {
  override suspend fun trackEvent(
    f8eEnvironment: F8eEnvironment,
    eventBundle: EventBundle,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .unauthenticated(f8eEnvironment = f8eEnvironment)
      .catching {
        post("/api/analytics/events") {
          setBody(
            eventBundle.encode()
          )
          contentType(MimeType.PROTOBUF)
        }
      }
      .logNetworkFailure { "Failed to send events to analytics proxy" }
      .mapUnit()
  }
}
