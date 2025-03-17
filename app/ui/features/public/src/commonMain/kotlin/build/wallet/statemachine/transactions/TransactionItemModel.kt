package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.*
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
  isLate: Boolean,
  onClick: () -> Unit,
) = ListItemModel(
  title = when (transactionType) {
    Incoming, Outgoing -> truncatedRecipientAddress
    UtxoConsolidation -> "Consolidation"
  },
  secondaryText = date,
  sideText = amount,
  secondarySideText = amountEquivalent,
  leadingAccessory = IconAccessory(
    model = IconModel(
      iconImage = when {
        transactionType is UtxoConsolidation && !isPending -> IconImage.LocalImage(Icon.BitcoinConsolidation)
        isLate || isPending -> IconImage.LocalImage(Icon.BitcoinBadged)
        else -> IconImage.LocalImage(Icon.Bitcoin)
      },
      iconSize = when {
        isLate || isPending || transactionType is UtxoConsolidation -> IconSize.Custom(48)
        else -> IconSize.Large
      },
      iconBackgroundType = IconBackgroundType.Square(
        size = IconSize.Custom(48),
        color = IconBackgroundType.Square.Color.Transparent,
        cornerRadius = 0
      ),
      iconAlignmentInBackground = IconAlignmentInBackground.Center,
      badge = when {
        isLate -> BadgeType.Error
        isPending -> BadgeType.Loading
        else -> null
      }
    )
  ),
  sideTextTint =
    when (transactionType) {
      Incoming -> GREEN
      Outgoing, UtxoConsolidation -> PRIMARY
    },
  onClick = onClick
)
