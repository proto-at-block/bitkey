package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.spending.SpendingKeyset

/**
 * A sweep transaction that can be signed by the user.
 */
data class SweepPsbt(
  /**
   * Partially signed transaction that will sweep all funds for a wallet to the
   * destination wallet
   */
  val psbt: Psbt,
  /** The signing factor that is able to sign the transaction */
  val signingFactor: PhysicalFactor,
  /** The keyset for the source wallet */
  val keyset: SpendingKeyset,
)
