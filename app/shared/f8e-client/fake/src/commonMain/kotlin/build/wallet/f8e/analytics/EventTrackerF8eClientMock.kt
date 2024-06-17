package build.wallet.f8e.analytics

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

class EventTrackerF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : EventTrackerF8eClient {
  val trackedEvents = turbine("track analytics event with f8e calls")

  var trackedEventResult: Result<Unit, NetworkingError>? = null

  override suspend fun trackEvent(
    f8eEnvironment: F8eEnvironment,
    eventBundle: EventBundle,
  ): Result<Unit, NetworkingError> {
    trackedEvents += eventBundle
    return trackedEventResult!!
  }
}
