package build.wallet.money.display

import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
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
   * Launches a non-blocking coroutine to continuously sync latest local [BitcoinDisplayUnit] value
   * into [bitcoinDisplayUnit]. This function should be called only once.
   */
  fun launchSync(scope: CoroutineScope)

  /**
   * Updates the persisted [BitcoinDisplayUnit].
   */
  suspend fun setBitcoinDisplayUnit(bitcoinDisplayUnit: BitcoinDisplayUnit)

  /**
   * Clears the persisted [BitcoinDisplayUnit].
   */
  suspend fun clear(): Result<Unit, Error>
}
