package bitkey.ui.verification

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App Segments used for Transaction Verification flows.
 */
object TxVerificationAppSegment : AppSegment {
  override val id: String = "TxVerification"

  /**
   * Settings/Management screen for changing/toggling the Transaction Verification Policy.
   */
  object ManagePolicy : AppSegment by childSegment("ManagePolicy")

  /**
   * Actual verification operations for transactions.
   */
  object Transaction : AppSegment by childSegment("Transaction")
}
