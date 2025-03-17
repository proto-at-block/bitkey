package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.spending.SpendingKeyset

/**
 * Represents a [Psbt] that will sweep funds from the source keyset to
 * a destination preset in the [psbt].
 */
data class SweepPsbt(
  /**
   * A psbt will sweep funds from the source keyset to the destination.
   */
  val psbt: Psbt,
  /**
   * Customer's physical factor (app or hardware) that is able to sign this psbt.
   * In the context of recovery, this is the factor that customer still has access to.
   */
  val signingFactor: PhysicalFactor,
  /**
   * Keyset from which funds will be swept.
   */
  val sourceKeyset: SpendingKeyset,
)
