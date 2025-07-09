package build.wallet.balance.utils

import build.wallet.activity.Transaction
import build.wallet.money.currency.FiatCurrency
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Service for managing mock chart scenarios.
 *
 * Uses seed-based generation for deterministic, configurable mock data.
 * Disabled in Customer builds. Persists selections across app launches.
 */
@Suppress("TooManyFunctions")
interface MockScenarioService {
  suspend fun currentMockConfiguration(): MockConfiguration?

  suspend fun currentPriceScenario(): MockPriceScenario?

  suspend fun currentTransactionScenario(): MockTransactionScenario?

  suspend fun currentDataQuality(): DataQuality?

  suspend fun currentSeed(): Long?

  suspend fun generateTransactions(): List<Transaction>

  suspend fun generatePriceData(
    maxPoints: Int,
    fiatCurrency: FiatCurrency,
    timeRange: Duration,
  ): List<MockDataPoint>

  /**
   * Flow that emits the current transaction scenario whenever it changes.
   * Emits null when no transaction scenario is set.
   */
  fun currentTransactionScenarioFlow(): Flow<MockTransactionScenario?>

  /**
   * Flow that emits the current price scenario whenever it changes.
   * Emits null when no price scenario is set.
   */
  fun currentPriceScenarioFlow(): Flow<MockPriceScenario?>

  /**
   * Flow that emits the current seed whenever it changes.
   * Emits null when no seed is set.
   */
  fun currentSeedFlow(): Flow<Long?>

  suspend fun setPriceScenario(scenario: MockPriceScenario)

  suspend fun setTransactionScenario(scenario: MockTransactionScenario)

  suspend fun setDataQuality(dataQuality: DataQuality)

  suspend fun setConfiguration(
    config: MockConfiguration,
    generateNewSeed: Boolean = false,
  )

  /**
   * Rotate the seed while keeping current scenarios.
   * This generates new mock data with the same scenarios but different randomization.
   */
  suspend fun rotateSeed()

  suspend fun clearScenarios(
    clearPrice: Boolean = true,
    clearTransaction: Boolean = true,
  )
}
