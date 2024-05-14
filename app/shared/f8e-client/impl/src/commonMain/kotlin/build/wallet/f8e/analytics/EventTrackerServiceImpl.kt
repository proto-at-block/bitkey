package build.wallet.f8e.analytics

import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.client.contentType
import build.wallet.ktor.result.setUnredactedBody
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import io.ktor.client.request.post

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
          withDescription("Send events to analytics proxy")
          setUnredactedBody(
            eventBundle.encode()
          )
          contentType(MimeType.PROTOBUF)
        }
      }
      .mapUnit()
  }
}
