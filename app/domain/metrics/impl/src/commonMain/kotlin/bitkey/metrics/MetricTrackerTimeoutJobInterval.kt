package bitkey.metrics

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Determines how frequently we should poll for metric timeouts.
 */
@JvmInline
value class MetricTrackerTimeoutJobInterval(val value: Duration = 60.seconds) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideMetricTrackerTimeoutJobInterval() = MetricTrackerTimeoutJobInterval()
  }
}
