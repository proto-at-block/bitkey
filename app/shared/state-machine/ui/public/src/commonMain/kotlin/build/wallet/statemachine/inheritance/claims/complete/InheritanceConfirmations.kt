package build.wallet.statemachine.inheritance.claims.complete

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.toFormattedString
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormMainContentModel.DataList

/**
 * Transaction data fields used in inheritance confirmation screens.
 */
fun inheritanceConfirmationContent(
  amount: String,
  fees: String,
  netReceivePrimary: String,
  netReceiveSecondary: String,
) = immutableListOf(
  DataList(
    items = immutableListOf(
      DataList.Data(
        title = "Arrival time",
        sideText = EstimatedTransactionPriority.inheritancePriority().toFormattedString()
      )
    )
  ),
  DataList(
    items = immutableListOf(
      DataList.Data(
        title = "Inheritance",
        sideText = amount
      ),
      DataList.Data(
        title = "Network fees",
        sideText = fees
      )
    ),
    total = DataList.Data(
      title = "Total received",
      sideText = netReceivePrimary,
      secondarySideText = netReceiveSecondary
    )
  )
)
