package build.wallet.statemachine.partnerships

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

object PartnershipsSegment : AppSegment {
  override val id: String = "Partnerships"

  object Sell : AppSegment by PartnershipsSegment.childSegment("Sell") {
    object LoadTransactionDetails : AppSegment by Sell.childSegment("LoadTransactionDetails")

    object TransferConfirmation : AppSegment by Sell.childSegment("TransferConfirmation")
  }
}
