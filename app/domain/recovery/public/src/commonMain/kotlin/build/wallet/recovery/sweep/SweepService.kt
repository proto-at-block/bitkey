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
   * Clears the cached [sweepRequired] flag after a sweep has been handled.
   * This is a lightweight operation that immediately dismisses the sweep UI indicator
   * without re-querying sweep status. Background sync will still periodically verify.
   */
  fun markSweepHandled()

  /**
   * Gets inactive keysets from F8e, and then prepares a sweep to consolidate funds from them
   * to the active keyset ([Keybox.activeSpendingKeyset]).
   *
   * If there are no funds to sweep, returns [Ok] with `null` value.
   *
   * @param keybox The keybox to perform the sweep for
   */
  suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error>

  /**
   * Estimates sweep fees by creating a mock destination keyset to force the sweep generator
   * to treat the currently active keyset as a source that needs to be swept.
   *
   * This method is specifically designed for wallet migration scenarios where we need to estimate
   * the cost of sweeping from the current active keyset to a new destination, but the new destination
   * keyset doesn't yet exist in a usable form.
   *
   * Returns the sweep details including fees, transfer amount, and PSBTs, or an error if:
   * - The sweep preparation fails
   * - There are no funds to sweep (zero balance) - [SweepError.NoFundsToSweep]
   * - Fees would exceed the available balance - [SweepError.NoFundsToSweep]
   *
   * @param keybox The keybox containing the active keyset to sweep from
   */
  suspend fun estimateSweepWithMockDestination(keybox: Keybox): Result<Sweep, SweepError>

  sealed interface SweepError {
    /**
     * Indicates there are no funds available to sweep, either because:
     * - The wallet balance is zero
     * - The fees required would exceed the available balance
     */
    object NoFundsToSweep : SweepError

    /**
     * Wraps errors from sweep generation or other underlying operations
     */
    data class SweepGenerationFailed(val cause: Error) : SweepError
  }
}
