package build.wallet.f8e.analytics

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

class EventTrackerServiceMock(
  turbine: (String) -> Turbine<Any>,
) : EventTrackerService {
  val trackedEvents = turbine("track analytics event with f8e calls")

  lateinit var trackedEventResult: Result<Unit, NetworkingError>

  override suspend fun trackEvent(
    f8eEnvironment: F8eEnvironment,
    eventBundle: EventBundle,
  ): Result<Unit, NetworkingError> {
    trackedEvents += eventBundle
    return trackedEventResult
  }
}
