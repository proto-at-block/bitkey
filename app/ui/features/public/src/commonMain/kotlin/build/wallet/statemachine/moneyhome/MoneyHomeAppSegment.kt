package build.wallet.statemachine.moneyhome

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App segments representing flows that originate from Money Home.
 */
object MoneyHomeAppSegment : AppSegment {
  override val id: String = "MoneyHome"

  object InitialWalletSync : AppSegment by MoneyHomeAppSegment.childSegment("InitialWalletSync")

  object Transactions : AppSegment by MoneyHomeAppSegment.childSegment("Transactions")

  /** Receiving funds: address display and hardware address verification. */
  object Receive : AppSegment by MoneyHomeAppSegment.childSegment("Receive")
}
