package build.wallet.analytics.events

import build.wallet.analytics.v1.EventBundle
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.analytics.EventTrackerF8eClient
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class AnalyticsEventProcessorImpl(
  private val eventTrackerF8eClient: EventTrackerF8eClient,
) : AnalyticsEventProcessor {
  override suspend fun processBatch(batch: List<QueueAnalyticsEvent>): Result<Unit, Error> {
    // return early if no items have been added to the batch for this processing interval
    if (batch.isEmpty()) return Ok(Unit)

    val f8eEnvironment = batch.first().f8eEnvironment

    return eventTrackerF8eClient.trackEvent(
      f8eEnvironment = f8eEnvironment,
      eventBundle = EventBundle(batch.map { it.event })
    )
  }
}
