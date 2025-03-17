package build.wallet.money.display

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages caching the value from [BitcoinDisplayPreferenceDao] in memory.
 */
interface BitcoinDisplayPreferenceRepository {
  /**
   * Emits latest local [BitcoinDisplayUnit] value, updated by [launchSync]
   * or [setBitcoinDisplayUnit].
   */
  val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit>

  /**
   * Updates the persisted [BitcoinDisplayUnit].
   */
  suspend fun setBitcoinDisplayUnit(bitcoinDisplayUnit: BitcoinDisplayUnit): Result<Unit, Error>

  /**
   * Clears the persisted [BitcoinDisplayUnit].
   */
  suspend fun clear(): Result<Unit, Error>
}
