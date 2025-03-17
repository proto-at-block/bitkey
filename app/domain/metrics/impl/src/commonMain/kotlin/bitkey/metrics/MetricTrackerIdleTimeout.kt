package bitkey.metrics

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Defines the idle timeout for a given metric. This is used to close metrics that remain open due
 * to user idling, process death, or crashes.
 */
@JvmInline
value class MetricTrackerIdleTimeout(val value: Duration = 10.minutes) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideMetricTrackerIdleTimeout() = MetricTrackerIdleTimeout()
  }
}
