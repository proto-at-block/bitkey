package build.wallet.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import kotlinx.coroutines.flow.StateFlow

/**
 * Determines if the user should be prompted to perform a sweep transaction
 * based on the state of the current wallet.
 */
interface SweepPromptRequirementCheck {
  /**
   * Whether the user should perform a sweep transaction.
   */
  val sweepRequired: StateFlow<Boolean>

  /**
   * Immediately check the wallet for funds that should be swept.
   *
   * This will update the internal known state and emit a new value
   * for [sweepRequired] when called, in addition to returning the
   * immediate value.
   */
  suspend fun checkForSweeps(keybox: Keybox): Boolean
}
