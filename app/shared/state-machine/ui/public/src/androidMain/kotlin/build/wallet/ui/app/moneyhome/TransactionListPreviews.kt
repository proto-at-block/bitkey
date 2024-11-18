package build.wallet.ui.app.moneyhome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TransactionListPreview() {
  PreviewWalletTheme {
    TransactionList(
      model =
        ListModel(
          headerText = "Recent activity",
          sections =
            immutableListOf(
              ListGroupModel(
                header = null,
                immutableListOf(
                  TransactionItemModel(
                    truncatedRecipientAddress = "3AH7...CkGJ",
                    date = "Pending",
                    amount = "$21.36",
                    amountEquivalent = "0.000305 BTC",
                    transactionType = Outgoing,
                    isPending = false,
                    onClick = {}
                  ),
                  TransactionItemModel(
                    truncatedRecipientAddress = "Ma3Y...D2pX",
                    date = "3 hours ago",
                    amount = " + $20.00",
                    amountEquivalent = "0.00017 BTC",
                    transactionType = Incoming,
                    isPending = false,
                    onClick = {}
                  )
                ),
                style = ListGroupStyle.NONE
              ),
              ListGroupModel(
                header = null,
                immutableListOf(
                  TransactionItemModel(
                    truncatedRecipientAddress = "Ma3Y...D2pX",
                    date = "July 21 at 1:25pm",
                    amount = "$250.00",
                    amountEquivalent = "0.00011 BTC",
                    transactionType = Outgoing,
                    isPending = false,
                    onClick = {}
                  ),
                  TransactionItemModel(
                    truncatedRecipientAddress = "3Kth...3gSa",
                    date = "July 4",
                    amount = "$102.14",
                    amountEquivalent = "0.000305 BTC",
                    transactionType = Outgoing,
                    isPending = false,
                    onClick = {}
                  )
                ),
                style = ListGroupStyle.NONE
              )
            )
        )
    )
  }
}
