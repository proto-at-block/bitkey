package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.ListHeader
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun TransactionList(
  modifier: Modifier = Modifier,
  model: ListModel,
) {
  Column(
    modifier = modifier
  ) {
    model.headerText?.let {
      ListHeader(title = it)
    }
    model.sections.forEach { section ->
      ListGroup(model = section)
    }
  }
}

@Preview
@Composable
internal fun TransactionListPreview() {
  PreviewWalletTheme {
    TransactionList(
      model =
        ListModel(
          headerText = "Recent activity",
          sections =
            immutableListOf(
              ListGroupModel(
                header = "Pending",
                immutableListOf(
                  TransactionItemModel(
                    truncatedRecipientAddress = "3AH7...CkGJ",
                    date = "Pending",
                    amount = "$21.36",
                    amountEquivalent = "0.000305 BTC",
                    incoming = false,
                    isPending = false,
                    onClick = {}
                  ),
                  TransactionItemModel(
                    truncatedRecipientAddress = "Ma3Y...D2pX",
                    date = "3 hours ago",
                    amount = " + $20.00",
                    amountEquivalent = "0.00017 BTC",
                    incoming = true,
                    isPending = false,
                    onClick = {}
                  )
                ),
                style = ListGroupStyle.NONE
              ),
              ListGroupModel(
                header = "Confirmed",
                immutableListOf(
                  TransactionItemModel(
                    truncatedRecipientAddress = "Ma3Y...D2pX",
                    date = "July 21 at 1:25pm",
                    amount = "$250.00",
                    amountEquivalent = "0.00011 BTC",
                    incoming = false,
                    isPending = false,
                    onClick = {}
                  ),
                  TransactionItemModel(
                    truncatedRecipientAddress = "3Kth...3gSa",
                    date = "July 4",
                    amount = "$102.14",
                    amountEquivalent = "0.000305 BTC",
                    incoming = false,
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
