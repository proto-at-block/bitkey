package build.wallet.ui.app.moneyhome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.transactions.PartnerTransactionItemModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TransactionListPreview() {
  PreviewWalletTheme {
    TransactionList(
      model = populatedTransactionListModel()
    )
  }
}

@Preview(name = "Empty Transaction List Light")
@Composable
fun EmptyTransactionListPreviewLight() {
  PreviewWalletTheme {
    TransactionList(
      model = emptyTransactionListModel()
    )
  }
}

@Preview(name = "Empty Transaction List Dark")
@Composable
fun EmptyTransactionListPreviewDark() {
  PreviewWalletTheme(
    theme = Theme.DARK
  ) {
    TransactionList(
      model = emptyTransactionListModel()
    )
  }
}

private fun populatedTransactionListModel() =
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
              isLate = false,
              onClick = {}
            ),
            TransactionItemModel(
              truncatedRecipientAddress = "Ma3Y...D2pX",
              date = "3 hours ago",
              amount = " + $20.00",
              amountEquivalent = "0.00017 BTC",
              transactionType = Incoming,
              isPending = false,
              isLate = false,
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
              isLate = false,
              onClick = {}
            ),
            TransactionItemModel(
              truncatedRecipientAddress = "3Kth...3gSa",
              date = "July 4",
              amount = "$102.14",
              amountEquivalent = "0.000305 BTC",
              transactionType = Outgoing,
              isPending = false,
              isLate = false,
              onClick = {}
            )
          ),
          style = ListGroupStyle.NONE
        ),
        ListGroupModel(
          header = null,
          immutableListOf(
            PartnerTransactionItemModel(
              title = "Purchase",
              date = "July 21 at 1:25pm",
              amount = "$250.00",
              amountEquivalent = "0.00011 BTC",
              isPending = false,
              logoUrl = null,
              sideTextTint = ListItemSideTextTint.GREEN,
              isError = false,
              onClick = {}
            ),
            previewPartnerTransactionItemModel(
              title = "Purchase",
              date = "July 21 at 1:25pm",
              amount = null,
              amountEquivalent = null,
              isPending = true,
              logoUrl = null,
              sideTextTint = ListItemSideTextTint.PRIMARY,
              isError = false,
              onClick = {}
            )
          ),
          style = ListGroupStyle.NONE
        )
      )
  )

private fun emptyTransactionListModel() =
  ListModel(
    headerText = "Recent activity",
    sections = immutableListOf()
  )

private fun previewPartnerIconImage() = IconImage.UrlImage(
  "https://images.ctfassets.net/mtmp6hzjjvnd/1lJRVmj6pcRnZcmL4eCEET/89cea9f73e867a8e70aa72971dba3586/CashLogo.svg",
  fallbackIcon = Icon.Bitcoin
)

private fun previewPartnerTransactionItemModel(
  title: String,
  date: String,
  logoUrl: String?,
  amount: String?,
  amountEquivalent: String?,
  isPending: Boolean,
  isError: Boolean,
  sideTextTint: ListItemSideTextTint,
  onClick: () -> Unit,
) = PartnerTransactionItemModel(
  title = title,
  date = date,
  logoUrl = logoUrl,
  amount = amount,
  amountEquivalent = amountEquivalent,
  isPending = isPending,
  isError = isError,
  sideTextTint = sideTextTint,
  onClick = onClick
).let { model ->
  val leadingAccessory = model.leadingAccessory as IconAccessory
  model.copy(
    leadingAccessory = leadingAccessory.copy(
      model = leadingAccessory.model.copy(iconImage = previewPartnerIconImage())
    )
  )
}
