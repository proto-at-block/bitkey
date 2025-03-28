package bitkey.metrics

import app.cash.turbine.test
import bitkey.metrics.MetricDefinitionFake.Variants.Variant1
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

class MetricTrackerDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val clock = ClockFake()
  val dao = MetricTrackerDao(
    databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory),
    clock = clock
  )

  val testMetric = TrackedMetric(
    name = MetricDefinitionFake.name,
    variant = Variant1.name
  )

  beforeTest {
    dao.clear()
  }

  test("store and retrieve metric") {
    dao.metrics.test {
      // Initially empty
      awaitItem().shouldBeEmpty()

      // Store metric
      dao.storeMetric(testMetric).value?.shouldBeNull()

      // Verify metric is stored
      awaitItem().shouldBe(listOf(testMetric))
    }
  }

  test("store metric returns previous value when exists") {
    // Store initial metric
    dao.storeMetric(testMetric)

    // Store updated metric
    val updatedMetric = testMetric.copy(variant = "new_variant")
    dao.storeMetric(updatedMetric).value.shouldBe(testMetric)

    dao.metrics.test {
      awaitItem().shouldBe(listOf(updatedMetric))
    }
  }

  test("set variant updates existing metric") {
    // Store initial metric
    dao.storeMetric(testMetric)

    // Update variant
    val newVariant = Variant1
    dao.setVariant(testMetric.name, Variant1)

    dao.metrics.test {
      awaitItem().shouldBe(
        listOf(
          testMetric.copy(variant = newVariant.name)
        )
      )
    }
  }

  test("set variant does nothing if metric doesn't exist") {
    dao.setVariant(testMetric.name, Variant1)

    dao.metrics.test {
      awaitItem().shouldBeEmpty()
    }
  }

  test("remove by name removes metric") {
    // Store metric
    dao.storeMetric(testMetric)

    // Remove metric
    dao.removeByName(testMetric.name).value?.shouldBe(testMetric)

    dao.metrics.test {
      awaitItem().shouldBeEmpty()
    }
  }

  test("remove by name returns null when metric doesn't exist") {
    dao.removeByName(testMetric.name).value.shouldBeNull()
  }

  test("get metrics past timeout") {
    // Store metric
    dao.storeMetric(testMetric)

    // No expired metrics with a regular timeout since the metrics was just created.
    dao.getMetricsPastTimeout(10.minutes).shouldBeEmpty()

    // Instead of doing clock manipulation, simply push the timeout into the future to force a timeout.
    dao.getMetricsPastTimeout((-10).minutes).shouldBe(listOf(testMetric))
  }

  test("clear removes all metrics") {
    // Store multiple metrics
    dao.storeMetric(testMetric)
    dao.storeMetric(
      TrackedMetric(
        name = MetricName("other_metric"),
        variant = "other_variant"
      )
    )

    // Clear all metrics
    dao.clear()

    dao.metrics.test {
      awaitItem().shouldBeEmpty()
    }
  }
})
