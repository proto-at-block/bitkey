package bitkey.metrics

import app.cash.turbine.test
import bitkey.datadog.ActionType
import bitkey.datadog.DatadogRumMonitorFake
import bitkey.metrics.MetricDefinitionFake.Variants.Variant1
import bitkey.metrics.MetricDefinitionFake.Variants.Variant2
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.MobileRealTimeMetricsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
class MetricTrackerServiceImplTests : FunSpec({
  val clock = ClockFake()
  lateinit var dao: MetricTrackerDaoImpl

  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)
  val mobileRealTimeMetricsFeatureFlag =
    MobileRealTimeMetricsFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
  val jobInterval = 1.seconds

  fun TestScope.service(
    appCoroutineScope: CoroutineScope = createBackgroundScope(),
    idleTimeout: Duration = 10.minutes,
  ): MetricTrackerServiceImpl {
    val sqlDriver = inMemorySqlDriver()
    dao = MetricTrackerDaoImpl(
      databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory),
      clock = clock
    )
    return MetricTrackerServiceImpl(
      datadogRumMonitor = datadogRumMonitor,
      dao = dao,
      appCoroutineScope = appCoroutineScope,
      metricTrackerTimeoutJobInterval = MetricTrackerTimeoutJobInterval(jobInterval),
      metricTrackerIdleTimeout = MetricTrackerIdleTimeout(idleTimeout),
      mobileRealTimeMetricsFeatureFlag = mobileRealTimeMetricsFeatureFlag
    )
  }

  beforeTest {
    mobileRealTimeMetricsFeatureFlag.reset()
    mobileRealTimeMetricsFeatureFlag.setFlagValue(true)
  }

  test("startMetric stores a new metric") {
    val metricTrackerService = service()

    metricTrackerService.startMetric(MetricDefinitionFake)
    dao.metrics.test {
      awaitUntil(listOf(TrackedMetric(name = MetricDefinitionFake.name, variant = null)))
    }
  }

  test("startMetric with existing metric cancels old metric") {
    val metricTrackerService = service()

    metricTrackerService.startMetric(MetricDefinitionFake, Variant1)
    dao.metrics.test {
      awaitUntil(
        listOf(
          TrackedMetric(
            name = MetricDefinitionFake.name,
            variant = Variant1.name
          )
        )
      )
    }

    metricTrackerService.startMetric(MetricDefinitionFake, Variant2)

    dao.metrics.test {
      awaitUntil(
        listOf(
          TrackedMetric(
            name = MetricDefinitionFake.name,
            variant = Variant2.name
          )
        )
      )
    }

    datadogRumMonitor.addUserActionCalls.awaitItem().shouldBe(
      Triple(
        ActionType.Custom,
        MetricDefinitionFake.name.name,
        mapOf(
          "outcome" to "restarted",
          "variant" to Variant1.name
        )
      )
    )
  }

  test("setVariant updates existing metric variant") {
    val metricTrackerService = service()

    metricTrackerService.startMetric(MetricDefinitionFake, Variant1)
    dao.metrics.test {
      awaitUntil(
        listOf(
          TrackedMetric(
            name = MetricDefinitionFake.name,
            variant = Variant1.name
          )
        )
      )
    }

    metricTrackerService.setVariant(MetricDefinitionFake, Variant2)
    dao.metrics.test {
      awaitUntil(
        listOf(
          TrackedMetric(
            name = MetricDefinitionFake.name,
            variant = Variant2.name
          )
        )
      )
    }
  }

  test("completeMetric removes metric and sends update") {
    val metricTrackerService = service()

    metricTrackerService.startMetric(MetricDefinitionFake, Variant1)
    dao.metrics.test {
      awaitUntil(
        listOf(
          TrackedMetric(
            name = MetricDefinitionFake.name,
            variant = Variant1.name
          )
        )
      )
    }

    metricTrackerService.completeMetric(MetricDefinitionFake, MetricOutcome.Succeeded)
    datadogRumMonitor.addUserActionCalls.awaitItem().shouldBe(
      Triple(
        ActionType.Custom,
        MetricDefinitionFake.name.name,
        mapOf(
          "outcome" to "succeeded",
          "variant" to Variant1.name
        )
      )
    )
    dao.metrics.test {
      awaitUntil(emptyList())
    }
  }

  test("executeWork cleans up timed out metrics") {
    val scope = createBackgroundScope()

    // Set the idle timeout to negative to force any started metrics to immediately
    // be considered timed out.
    val metricTrackerService = service(scope, idleTimeout = (-1).seconds)

    metricTrackerService.startMetric(MetricDefinitionFake)
    scope.launch {
      metricTrackerService.executeWork()
    }

    dao.metrics.test {
      awaitUntil(emptyList())
    }

    datadogRumMonitor.addUserActionCalls.awaitItem().shouldBe(
      Triple(
        ActionType.Custom,
        MetricDefinitionFake.name.name,
        mapOf(
          "outcome" to "timeout"
        )
      )
    )
  }

  test("metrics are not processed when feature flag is disabled") {
    mobileRealTimeMetricsFeatureFlag.setFlagValue(false)
    val metricTrackerService = service()

    metricTrackerService.startMetric(MetricDefinitionFake)
    metricTrackerService.completeMetric(MetricDefinitionFake, MetricOutcome.Succeeded)

    datadogRumMonitor.addUserActionCalls.awaitNoEvents(jobInterval)
  }
})
