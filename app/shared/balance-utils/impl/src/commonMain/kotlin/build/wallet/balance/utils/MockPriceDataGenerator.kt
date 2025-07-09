package build.wallet.balance.utils

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.currency.FiatCurrency
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration

/**
 * Generates deterministic mock price data for testing chart functionality.
 *
 * Uses seed-based generation with timestamp snapping to ensure temporal consistency -
 * same seed always produces identical historical data regardless of when it's generated.
 *
 * Timestamps are snapped to predefined intervals to ensure deterministic historical ranges.
 */
@BitkeyInject(AppScope::class)
class MockPriceDataGenerator(
  private val clock: Clock,
) {
  companion object {
    /**
     * Different snapping strategies for different time ranges.
     *
     * HOURLY: Snaps to hourly intervals - good for short-term data
     * DAILY: Snaps to daily intervals - good for medium-term data
     * WEEKLY: Snaps to weekly intervals - good for long-term data
     */
    enum class SnapStrategy(val intervalMillis: Long) {
      HOURLY(60 * 60 * 1000L), // 1 hour
      DAILY(24 * 60 * 60 * 1000L), // 24 hours
      WEEKLY(7 * 24 * 60 * 60 * 1000L), // 7 days
    }

    /**
     * Choose appropriate snap strategy based on time range.
     * Shorter ranges use finer granularity, longer ranges use coarser granularity.
     */
    private fun getSnapStrategyForTimeRange(timeRange: Duration): SnapStrategy {
      return when {
        timeRange <= Duration.parse("24h") -> SnapStrategy.HOURLY
        timeRange <= Duration.parse("30d") -> SnapStrategy.DAILY
        else -> SnapStrategy.WEEKLY
      }
    }

    /**
     * Snaps a timestamp to the nearest predefined interval based on time range.
     * This ensures that data generated at different times within the same interval
     * will use the same reference point, making historical data deterministic.
     */
    private fun snapTimestampToInterval(
      timestamp: Instant,
      timeRange: Duration,
    ): Instant {
      val strategy = getSnapStrategyForTimeRange(timeRange)
      val millis = timestamp.toEpochMilliseconds()
      val snappedMillis = (millis / strategy.intervalMillis) * strategy.intervalMillis
      return Instant.fromEpochMilliseconds(snappedMillis)
    }
  }

  /**
   * Generate mock price data points for a given scenario.
   *
   * @param scenario The price scenario to simulate
   * @param dataQuality The quality/density of data to generate
   * @param seed The seed for deterministic generation
   * @param maxPoints Maximum number of data points to generate
   * @param fiatCurrency The fiat currency for price values
   * @param timeRange The time range to generate data for
   */
  fun generateMockPriceData(
    scenario: MockPriceScenario,
    dataQuality: DataQuality,
    seed: Long,
    maxPoints: Int,
    fiatCurrency: FiatCurrency,
    timeRange: Duration,
  ): List<MockDataPoint> {
    // Snap the current time to ensure deterministic historical ranges
    val snappedNow = snapTimestampToInterval(clock.now(), timeRange)
    val endTime = snappedNow
    val startTime = endTime.minus(timeRange)

    // Generate timestamps evenly distributed across the time range
    val timeStep = (endTime.toEpochMilliseconds() - startTime.toEpochMilliseconds()) / maxPoints
    val startMillis = startTime.toEpochMilliseconds()
    val timestamps = (0 until maxPoints).map { i ->
      Instant.fromEpochMilliseconds(startMillis + (i * timeStep))
    }

    // Generate price data points using deterministic seeds
    return timestamps.mapNotNull { timestamp ->
      if (shouldIncludeTimestamp(dataQuality, combineSeeds(seed, timestamp))) {
        generateDataPointForTimestamp(
          scenario = scenario,
          seed = seed,
          timestamp = timestamp,
          fiatCurrency = fiatCurrency,
          referenceTime = snappedNow // Use snapped time for deterministic reference
        )
      } else {
        null
      }
    }
  }

  private fun shouldIncludeTimestamp(
    dataQuality: DataQuality,
    timestampSeed: Long,
  ): Boolean {
    val random = Random(timestampSeed)
    return when (dataQuality) {
      DataQuality.Perfect -> true
      DataQuality.SparseData -> random.nextFloat() > 0.4f
      DataQuality.ExcessiveData -> true
    }
  }

  private fun generateDataPointForTimestamp(
    scenario: MockPriceScenario,
    seed: Long,
    timestamp: Instant,
    fiatCurrency: FiatCurrency,
    referenceTime: Instant,
  ): MockDataPoint {
    val timestampSeed = combineSeeds(seed, timestamp)
    val random = Random(timestampSeed)

    val price = generateScenarioPriceForTimestamp(
      scenario = scenario,
      timestamp = timestamp,
      fiatCurrency = fiatCurrency,
      random = random,
      referenceTime = referenceTime
    )

    return MockDataPoint(
      timestamp = timestamp.epochSeconds,
      price = price
    )
  }

  private fun generateScenarioPriceForTimestamp(
    scenario: MockPriceScenario,
    timestamp: Instant,
    fiatCurrency: FiatCurrency,
    random: Random,
    referenceTime: Instant,
  ): Double {
    val basePrice = getBasePriceForCurrency(fiatCurrency)
    val daysSinceEpoch = timestamp.toEpochMilliseconds() / (1000 * 60 * 60 * 24)
    val referenceDaysSinceEpoch = referenceTime.toEpochMilliseconds() / (1000 * 60 * 60 * 24)
    val daysFromReference = (daysSinceEpoch - referenceDaysSinceEpoch).toDouble()

    val price = when (scenario) {
      MockPriceScenario.BULL_MARKET -> {
        val trend = basePrice * 1.002.pow(daysFromReference)
        val noise = generateGaussianNoise(random) * 0.03
        trend * (1.0 + noise)
      }
      MockPriceScenario.BEAR_MARKET -> {
        val trend = basePrice * 0.999.pow(daysFromReference)
        val noise = generateGaussianNoise(random) * 0.04
        trend * (1.0 + noise)
      }
      MockPriceScenario.VOLATILE_MARKET -> {
        val cyclical = sin(daysSinceEpoch * 0.05) * 0.15
        val noise = generateGaussianNoise(random) * 0.08
        basePrice * (1.0 + cyclical + noise)
      }
      MockPriceScenario.SIDEWAYS_MARKET -> {
        val drift = sin(daysSinceEpoch * 0.01) * 0.05
        val noise = generateGaussianNoise(random) * 0.025
        basePrice * (1.0 + drift + noise)
      }
    }

    return if (price > 1000.0) price else 1000.0 // Floor price
  }

  private fun getBasePriceForCurrency(fiatCurrency: FiatCurrency): Double {
    return when (fiatCurrency.textCode.code) {
      "USD" -> 104000.0
      "EUR" -> 98000.0
      "GBP" -> 82000.0
      "CAD" -> 143000.0
      "AUD" -> 159000.0
      "JPY" -> 15800000.0
      else -> 104000.0
    }
  }

  private fun combineSeeds(
    baseSeed: Long,
    timestamp: Instant,
  ): Long {
    return baseSeed xor timestamp.toEpochMilliseconds()
  }

  private fun generateGaussianNoise(random: Random): Double {
    val u1 = random.nextDouble()
    val u2 = random.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
  }
}
