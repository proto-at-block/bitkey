package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.analytics.v1.EventBundle
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.analytics.EventTrackerF8eClient
import com.github.michaelbull.result.Result

class AnalyticsEventProcessorImpl(
  private val eventTrackerF8eClient: EventTrackerF8eClient,
) : AnalyticsEventProcessor {
  override suspend fun processBatch(batch: List<QueueAnalyticsEvent>): Result<Unit, Error> {
    val f8eEnvironment = batch.first().f8eEnvironment
    return eventTrackerF8eClient.trackEvent(
      f8eEnvironment = f8eEnvironment,
      eventBundle = EventBundle(batch.map { it.event })
    )
  }
}

data class QueueAnalyticsEvent(
  val f8eEnvironment: F8eEnvironment,
  val event: Event,
)
