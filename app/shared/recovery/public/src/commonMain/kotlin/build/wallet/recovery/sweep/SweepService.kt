package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Result

interface SweepService {
  /**
   * Prepares a sweep to consolidate funds from inactive keysets ([Keybox.inactiveKeysets]) to the
   * active keyset ([Keybox.activeSpendingKeyset]).
   *
   * If there are no funds to sweep, returns [Ok] with `null` value.
   */
  suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error>
}
