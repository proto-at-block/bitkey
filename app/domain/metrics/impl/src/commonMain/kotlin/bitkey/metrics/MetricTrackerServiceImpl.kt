package bitkey.metrics

import bitkey.datadog.ActionType
import bitkey.datadog.DatadogRumMonitor
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.MobileRealTimeMetricsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logWarn
import com.github.michaelbull.result.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class MetricTrackerServiceImpl(
  private val datadogRumMonitor: DatadogRumMonitor,
  private val dao: MetricTrackerDaoImpl,
  private val appCoroutineScope: CoroutineScope,
  private val metricTrackerTimeoutJobInterval: MetricTrackerTimeoutJobInterval,
  private val metricTrackerIdleTimeout: MetricTrackerIdleTimeout,
  private val mobileRealTimeMetricsFeatureFlag: MobileRealTimeMetricsFeatureFlag,
) : MetricTrackerService, MetricTrackerTimeoutPoller {
  override suspend fun executeWork() {
    appCoroutineScope.launchTicker(metricTrackerTimeoutJobInterval.value) {
      if (mobileRealTimeMetricsFeatureFlag.isEnabled()) {
        val expiredMetrics = dao.getMetricsPastTimeout(metricTrackerIdleTimeout.value)
        val outcome = MetricOutcome.Timeout
        expiredMetrics.forEach {
          metricUpdate(it, outcome)
          dao.removeByName(it.name)
        }
      }
    }
  }

  override fun <T : MetricDefinition> startMetric(
    metricDefinition: T,
    variant: Variant<T>?,
  ) {
    if (!mobileRealTimeMetricsFeatureFlag.isEnabled()) {
      return
    }

    appCoroutineScope.launch {
      val newMetric = TrackedMetric(
        name = metricDefinition.name,
        variant = variant?.name
      )

      // Somehow, there is already a metric in flight for the provided [metricName]. Complete it
      // as restarted.
      dao.storeMetric(newMetric).get()?.let {
        logWarn { "Encountered existing metricInFlight: $it" }
        metricUpdate(
          trackedMetric = it,
          outcome = MetricOutcome.Restarted
        )
      }
    }
  }

  override fun <T : MetricDefinition> setVariant(
    metricDefinition: T,
    variant: Variant<T>,
  ) {
    appCoroutineScope.launch {
      dao.setVariant(metricDefinition.name, variant)
    }
  }

  override fun completeMetric(
    metricDefinition: MetricDefinition,
    outcome: MetricOutcome,
  ) {
    if (!mobileRealTimeMetricsFeatureFlag.isEnabled()) {
      return
    }

    appCoroutineScope.launch {
      dao.removeByName(metricDefinition.name).get()?.let {
        metricUpdate(it, outcome)
      }
    }
  }

  override suspend fun clearMetrics() {
    dao.clear()
  }

  private fun metricUpdate(
    trackedMetric: TrackedMetric,
    outcome: MetricOutcome,
  ) {
    datadogRumMonitor
      .addUserAction(
        type = ActionType.Custom,
        name = trackedMetric.name.name,
        attributes = listOfNotNull(
          "outcome" to outcome.toString().lowercase(),
          trackedMetric.variant?.let { "variant" to it }
        ).toMap()
      )
  }
}
