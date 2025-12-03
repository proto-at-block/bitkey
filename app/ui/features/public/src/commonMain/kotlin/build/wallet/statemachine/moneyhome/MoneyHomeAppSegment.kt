package build.wallet.statemachine.moneyhome

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * App segments representing flows that originate from Money Home.
 */
object MoneyHomeAppSegment : AppSegment {
  override val id: String = "MoneyHome"

  object Transactions : AppSegment by MoneyHomeAppSegment.childSegment("Transactions")
}
