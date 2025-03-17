package build.wallet.recovery

import build.wallet.bitcoin.transactions.Psbt

/**
 * Used to encapsulate all the objects returned at the
 * end of the Complete App Delay and Notify Recovery
 * calls on Hardware
 */
@Suppress("unused") // used in Swift code
data class CompletedAppDelayNotifyOnHardware(
  /** This is the PSBT signed by the hardware */
  val psbt: Psbt,
  /** Determines if the CompleteAppRecovery call was successful */
  val completedAppRecovery: Boolean,
  /** This is the sealed CSEK used for Cloud backup */
  val sealedCsek: ByteArray,
)
