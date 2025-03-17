package build.wallet.money.display

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface BitcoinDisplayPreferenceDao {
  /**
   * Emits customer's preferred display unit for Bitcoin amounts.
   * Errors are logged but not emitted.
   */
  fun bitcoinDisplayPreference(): Flow<BitcoinDisplayUnit?>

  /**
   * Sets the given unit as the customer's preferred Bitcoin display unit.
   */
  suspend fun setBitcoinDisplayPreference(unit: BitcoinDisplayUnit): Result<Unit, Error>

  /**
   * Clears the customer's preferred Bitcoin display unit.
   */
  suspend fun clear(): Result<Unit, Error>
}
