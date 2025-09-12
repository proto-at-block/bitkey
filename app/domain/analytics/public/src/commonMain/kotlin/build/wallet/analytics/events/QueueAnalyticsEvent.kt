package build.wallet.analytics.events

import build.wallet.analytics.v1.Event
import build.wallet.f8e.F8eEnvironment

data class QueueAnalyticsEvent(
  val f8eEnvironment: F8eEnvironment,
  val event: Event,
)
