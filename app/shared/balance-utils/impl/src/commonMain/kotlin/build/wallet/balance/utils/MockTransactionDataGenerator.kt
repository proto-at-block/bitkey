package build.wallet.balance.utils

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration

/**
 * Simplified transaction data for mock generation within the balance utils module.
 */
data class MockTransactionData(
  val id: String,
  val timestamp: Instant,
  val amountSats: Long, // Positive for incoming, negative for outgoing
  val feeSats: Long,
  val isIncoming: Boolean,
)

/**
 * Generates deterministic mock Bitcoin transaction data for testing balance history functionality.
 *
 * Uses seed-based generation with timestamp snapping to ensure temporal consistency -
 * same seed always produces identical historical transactions regardless of when it's generated.
 *
 * Timestamps are snapped to predefined intervals to ensure deterministic historical ranges.
 */
@BitkeyInject(AppScope::class)
class MockTransactionDataGenerator(
  private val clock: Clock,
) {
  companion object {
    // Snap generation times to daily intervals to ensure consistency
    // This means data generated at any time during a day will use the same "reference time"
    private const val SNAP_INTERVAL_HOURS = 24L
    private const val SNAP_INTERVAL_MILLIS = SNAP_INTERVAL_HOURS * 60 * 60 * 1000

    /**
     * Snaps a timestamp to the nearest predefined interval.
     * This ensures that data generated at different times within the same interval
     * will use the same reference point, making historical data deterministic.
     */
    private fun snapTimestampToInterval(timestamp: Instant): Instant {
      val millis = timestamp.toEpochMilliseconds()
      val snappedMillis = (millis / SNAP_INTERVAL_MILLIS) * SNAP_INTERVAL_MILLIS
      return Instant.fromEpochMilliseconds(snappedMillis)
    }
  }

  fun generateMockTransactionData(
    scenario: MockTransactionScenario,
    seed: Long,
  ): List<MockTransactionData> {
    if (scenario == MockTransactionScenario.EMPTY_WALLET) {
      return emptyList()
    }

    val random = Random(seed)
    // Snap the current time to ensure deterministic historical ranges
    val snappedNow = snapTimestampToInterval(clock.now())
    val timeSpan = Duration.parse("365d") // Generate over past year

    val transactionCount = getTransactionCountForScenario(scenario, random)
    val transactions = mutableListOf<MockTransactionData>()
    var currentBalanceSats = 0L

    // Generate transactions chronologically (oldest first) to track balance properly
    for (i in 0 until transactionCount) {
      val txSeed = combineSeeds(seed, i.toLong())
      val txRandom = Random(txSeed)

      // Deterministic transaction time (chronologically distributed)
      val timeRatio = i.toDouble() / transactionCount
      val transactionTime = snappedNow.minus(timeSpan).plus(
        Duration.parse("${(timeSpan.inWholeMilliseconds * timeRatio).toLong()}ms")
      )

      val transaction = generateSingleTransaction(
        scenario = scenario,
        txRandom = txRandom,
        timestamp = transactionTime,
        currentBalanceSats = currentBalanceSats,
        seed = seed
      )

      if (transaction != null) {
        transactions.add(transaction)
        currentBalanceSats = updateBalanceFromTransaction(currentBalanceSats, transaction)
      }
    }

    return transactions.sortedBy { it.timestamp }
  }

  private fun generateSingleTransaction(
    scenario: MockTransactionScenario,
    txRandom: Random,
    timestamp: Instant,
    currentBalanceSats: Long,
    seed: Long,
  ): MockTransactionData? {
    // Deterministic decision: incoming or outgoing
    val isIncoming = shouldBeIncomingTransaction(scenario, txRandom, currentBalanceSats)

    return if (isIncoming) {
      generateIncomingTransaction(scenario, txRandom, timestamp, seed)
    } else {
      // For outgoing, ensure we don't spend more than we have
      if (currentBalanceSats <= 0) {
        // Force incoming transaction if no balance
        generateIncomingTransaction(scenario, txRandom, timestamp, seed)
      } else {
        generateOutgoingTransaction(scenario, txRandom, timestamp, currentBalanceSats, seed)
      }
    }
  }

  private fun shouldBeIncomingTransaction(
    scenario: MockTransactionScenario,
    random: Random,
    currentBalanceSats: Long,
  ): Boolean {
    // If balance is very low, heavily bias toward incoming
    if (currentBalanceSats < 100_000) { // Less than 0.001 BTC
      return random.nextFloat() < 0.85f // 85% chance of incoming
    }

    return when (scenario) {
      MockTransactionScenario.HODLER -> random.nextFloat() < 0.9f // 90% incoming (mostly receives)
      MockTransactionScenario.DAY_TRADER -> random.nextFloat() < 0.6f // 60% incoming (frequent trades)
      MockTransactionScenario.WHALE -> random.nextFloat() < 0.7f // 70% incoming (large accumulation)
      MockTransactionScenario.CASUAL_USER -> random.nextFloat() < 0.65f // 65% incoming (normal usage)
      MockTransactionScenario.EMPTY_WALLET -> true // Should not reach here
    }
  }

  private fun generateIncomingTransaction(
    scenario: MockTransactionScenario,
    random: Random,
    timestamp: Instant,
    seed: Long,
  ): MockTransactionData {
    val amountSats = generateIncomingAmount(scenario, random)
    val feeSats = generateRealisticFee(random, scenario)

    return MockTransactionData(
      id = generateDeterministicTxId(seed, timestamp),
      timestamp = timestamp,
      amountSats = amountSats,
      feeSats = feeSats,
      isIncoming = true
    )
  }

  private fun generateOutgoingTransaction(
    scenario: MockTransactionScenario,
    random: Random,
    timestamp: Instant,
    availableBalanceSats: Long,
    seed: Long,
  ): MockTransactionData {
    val feeSats = generateRealisticFee(random, scenario)
    val maxSpendableSats = availableBalanceSats - feeSats

    if (maxSpendableSats <= 0) {
      // Fallback to incoming if can't afford fee
      return generateIncomingTransaction(scenario, random, timestamp, seed)
    }

    val amountSats = generateOutgoingAmount(scenario, random, maxSpendableSats)

    return MockTransactionData(
      id = generateDeterministicTxId(seed, timestamp),
      timestamp = timestamp,
      amountSats = -amountSats, // Negative for outgoing
      feeSats = feeSats,
      isIncoming = false
    )
  }

  private fun generateIncomingAmount(
    scenario: MockTransactionScenario,
    random: Random,
  ): Long {
    return when (scenario) {
      MockTransactionScenario.WHALE -> random.nextLong(10_000_000, 1_000_000_000) // 0.1-10 BTC
      MockTransactionScenario.DAY_TRADER -> random.nextLong(100_000, 2_000_000) // 0.001-0.02 BTC (small trades)
      else -> random.nextLong(100_000, 10_000_000) // 0.001-0.1 BTC
    }
  }

  private fun generateOutgoingAmount(
    scenario: MockTransactionScenario,
    random: Random,
    maxSpendableSats: Long,
  ): Long {
    val satoshiAmount = when (scenario) {
      MockTransactionScenario.WHALE -> {
        // Spend 20-90% of available balance for whale behavior
        val percentage = random.nextDouble(0.2, 0.9)
        (maxSpendableSats * percentage).toLong()
      }
      MockTransactionScenario.DAY_TRADER -> {
        // Day trading: 5-25% of balance
        val percentage = random.nextDouble(0.05, 0.25)
        (maxSpendableSats * percentage).toLong()
      }
      else -> {
        // Regular spending: 5-40% of balance
        val percentage = random.nextDouble(0.05, 0.4)
        (maxSpendableSats * percentage).toLong()
      }
    }

    return maxOf(satoshiAmount, 546) // Ensure above dust limit
  }

  private fun updateBalanceFromTransaction(
    currentBalanceSats: Long,
    transaction: MockTransactionData,
  ): Long {
    return if (transaction.isIncoming) {
      currentBalanceSats + transaction.amountSats
    } else {
      currentBalanceSats + transaction.amountSats - transaction.feeSats // amountSats is negative for outgoing
    }
  }

  private fun getTransactionCountForScenario(
    scenario: MockTransactionScenario,
    random: Random,
  ): Int {
    return when (scenario) {
      MockTransactionScenario.EMPTY_WALLET -> 0
      MockTransactionScenario.HODLER -> random.nextInt(3, 8) // Very few transactions
      MockTransactionScenario.DAY_TRADER -> random.nextInt(80, 151) // Many small trades
      MockTransactionScenario.WHALE -> random.nextInt(5, 16) // Few large transactions
      MockTransactionScenario.CASUAL_USER -> random.nextInt(20, 41) // Normal usage
    }
  }

  private fun generateDeterministicTxId(
    baseSeed: Long,
    timestamp: Instant,
  ): String {
    val combined = combineSeeds(baseSeed, timestamp.toEpochMilliseconds())
    return combined.toString(16).padStart(64, '0').take(64)
  }

  private fun generateRealisticFee(
    random: Random,
    scenario: MockTransactionScenario,
  ): Long {
    // Fee between 1-15 sats/vB, assume ~200 vB transaction
    val baseFeeRate = when (scenario) {
      MockTransactionScenario.DAY_TRADER -> random.nextInt(5, 21) // Higher fees for quick confirmation
      else -> random.nextInt(1, 16) // Normal fee range
    }

    val txSize = random.nextInt(180, 250)
    return (baseFeeRate * txSize).toLong()
  }

  private fun combineSeeds(
    baseSeed: Long,
    value: Long,
  ): Long {
    return baseSeed xor value
  }
}
