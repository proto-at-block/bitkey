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
        title = "Arrival Time",
        sideText = EstimatedTransactionPriority.inheritancePriority().toFormattedString()
      )
    )
  ),
  DataList(
    items = immutableListOf(
      DataList.Data(
        title = "Recipient Receives",
        sideText = amount
      ),
      DataList.Data(
        title = "Network Fees",
        sideText = fees
      )
    ),
    total = DataList.Data(
      title = "Total",
      sideText = netReceivePrimary,
      secondarySideText = netReceiveSecondary
    )
  )
)
