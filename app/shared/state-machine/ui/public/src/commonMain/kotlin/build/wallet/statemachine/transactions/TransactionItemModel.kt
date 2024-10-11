package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.statemachine.core.Icon.*
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Large
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint.GREEN
import build.wallet.ui.model.list.ListItemSideTextTint.PRIMARY

fun TransactionItemModel(
  truncatedRecipientAddress: String,
  date: String,
  amount: String,
  amountEquivalent: String,
  transactionType: TransactionType,
  isPending: Boolean,
  onClick: () -> Unit,
) = ListItemModel(
  title = when (transactionType) {
    Incoming, Outgoing -> truncatedRecipientAddress
    UtxoConsolidation -> "Consolidation"
  },
  secondaryText = date,
  sideText = amount,
  secondarySideText = amountEquivalent,
  leadingAccessory =
    IconAccessory(
      model =
        IconModel(
          iconImage = if (isPending) {
            IconImage.Loader
          } else {
            when (transactionType) {
              Incoming -> IconImage.LocalImage(SmallIconArrowDown)
              Outgoing -> IconImage.LocalImage(SmallIconArrowUp)
              UtxoConsolidation -> IconImage.LocalImage(SmallIconConsolidation)
            }
          },
          iconSize = Small,
          iconBackgroundType = Circle(circleSize = Large)
        )
    ),
  sideTextTint =
    when (transactionType) {
      Incoming -> GREEN
      Outgoing, UtxoConsolidation -> PRIMARY
    },
  onClick = onClick
)
