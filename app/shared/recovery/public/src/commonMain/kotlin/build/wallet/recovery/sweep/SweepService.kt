package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

interface SweepService {
  /**
   * Emits whether the customer should perform a sweep transaction.
   * Updated by [SweepSyncWorker] or by on-demand check [checkForSweeps].
   */
  val sweepRequired: StateFlow<Boolean>

  /**
   * An on-demand checks whether the customer should perform a sweep transaction.
   * Updates [sweepRequired] value.
   */
  suspend fun checkForSweeps()

  /**
   * Prepares a sweep to consolidate funds from inactive keysets ([Keybox.inactiveKeysets]) to the
   * active keyset ([Keybox.activeSpendingKeyset]).
   *
   * If there are no funds to sweep, returns [Ok] with `null` value.
   */
  suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error>
}
