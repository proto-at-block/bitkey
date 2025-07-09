package build.wallet.balance.utils

import kotlinx.datetime.Instant
import kotlin.random.Random

/**
 * Price scenarios focus on market conditions and data quality.
 */
enum class MockPriceScenario(
  val displayName: String,
) {
  // Core market conditions
  BULL_MARKET("Bull Market"),
  BEAR_MARKET("Bear Market"),
  SIDEWAYS_MARKET("Sideways Market"),
  VOLATILE_MARKET("Volatile Market"),
}

/**
 * Transaction scenarios focus on spending/receiving patterns.
 */
enum class MockTransactionScenario(
  val displayName: String,
) {
  // Core user behaviors
  HODLER("HODLer - Minimal Activity"),
  CASUAL_USER("Casual User - Moderate Activity"),
  DAY_TRADER("Day Trader - Frequent Trades"),
  WHALE("Whale - Large Transactions"),

  // Edge cases
  EMPTY_WALLET("Empty Wallet - No History"),
}

/**
 * Data quality variations for testing chart robustness.
 */
enum class DataQuality(
  val displayName: String,
) {
  Perfect("Perfect Data"),
  SparseData("Missing Price Data"),
  ExcessiveData("Extra Price Data"),
}

/**
 * Combined mock configuration with independent price and transaction scenarios.
 */
data class MockConfiguration(
  val priceScenario: MockPriceScenario?,
  val transactionScenario: MockTransactionScenario?,
  val dataQuality: DataQuality,
  val seed: Long,
  val generatedAt: Instant,
) {
  companion object {
    fun generate(
      priceScenario: MockPriceScenario,
      transactionScenario: MockTransactionScenario,
      dataQuality: DataQuality,
      clock: kotlinx.datetime.Clock,
    ): MockConfiguration {
      return MockConfiguration(
        priceScenario = priceScenario,
        transactionScenario = transactionScenario,
        dataQuality = dataQuality,
        seed = Random.nextLong(),
        generatedAt = clock.now()
      )
    }

    fun fromString(configString: String): MockConfiguration? {
      val parts = configString.split(":")
      if (parts.size != 5) return null

      val priceScenario = MockPriceScenario.entries.find { it.name == parts[0] } ?: return null
      val transactionScenario = MockTransactionScenario.entries.find {
        it.name == parts[1]
      } ?: return null
      val dataQuality = DataQuality.entries.find { it.name == parts[2] } ?: return null
      val seed = parts[3].toLongOrNull() ?: return null
      val timestamp = runCatching { kotlinx.datetime.Instant.parse(parts[4]) }.getOrNull() ?: return null

      return MockConfiguration(priceScenario, transactionScenario, dataQuality, seed, timestamp)
    }
  }

  fun toShareableString(): String {
    return "${priceScenario?.name}:${transactionScenario?.name}:${dataQuality.name}:$seed:$generatedAt"
  }
}
