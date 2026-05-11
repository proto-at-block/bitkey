package build.wallet.statemachine.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
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
  pendingBadgeType: BadgeType = BadgeType.Loading,
  isLoading: Boolean = false,
  onClick: () -> Unit,
) = ListItemModel(
  title = transactionType.title(),
  titleLabel = transactionType.titleLabel(truncatedRecipientAddress),
  secondaryText = date,
  sideText = amount,
  secondarySideText = amountEquivalent,
  leadingAccessory = IconAccessory(
    model = transactionType.iconModel(isPending, isLate, pendingBadgeType)
  ),
  sideTextTint = transactionType.sideTextTint(),
  onClick = onClick,
  isLoading = isLoading
)

private fun TransactionType.title(): String =
  when (this) {
    Incoming, Outgoing -> "" // titleLabel will be used
    UtxoConsolidation -> "Consolidation"
  }

private fun TransactionType.titleLabel(
  truncatedRecipientAddress: String,
): LabelModel.StringWithStyledSubstringModel? =
  when (this) {
    Incoming, Outgoing -> LabelModel.StringWithStyledSubstringModel(
      string = truncatedRecipientAddress,
      styledSubstrings = listOf(
        LabelModel.StringWithStyledSubstringModel.StyledSubstring(
          range = 0..<truncatedRecipientAddress.length,
          style = LabelModel.StringWithStyledSubstringModel.SubstringStyle.FontFeatureStyle("\"calt\" 0")
        )
      )
    )
    UtxoConsolidation -> null
  }

private fun TransactionType.iconModel(
  isPending: Boolean,
  isLate: Boolean,
  pendingBadgeType: BadgeType,
): IconModel =
  IconModel(
    iconImage = iconImage(isPending, isLate),
    iconSize = iconSize(isLate, isPending),
    iconBackgroundType = IconBackgroundType.Square(
      size = IconSize.Custom(48),
      color = IconBackgroundType.Square.Color.Transparent,
      cornerRadius = 0
    ),
    iconAlignmentInBackground = IconAlignmentInBackground.Start,
    badge = badge(isPending, isLate, pendingBadgeType)
  )

private fun TransactionType.iconImage(
  isPending: Boolean,
  isLate: Boolean,
): IconImage =
  when {
    this is UtxoConsolidation && !isPending -> IconImage.LocalImage(Icon.BitcoinConsolidation)
    isLate || isPending -> IconImage.LocalImage(Icon.BitcoinBadged)
    else -> IconImage.LocalImage(Icon.Bitcoin)
  }

private fun TransactionType.iconSize(
  isLate: Boolean,
  isPending: Boolean,
): IconSize =
  when {
    isLate || isPending || this is UtxoConsolidation -> IconSize.Custom(48)
    else -> IconSize.Custom(44)
  }

private fun badge(
  isPending: Boolean,
  isLate: Boolean,
  pendingBadgeType: BadgeType,
): BadgeType? =
  when {
    isLate -> BadgeType.Error
    isPending -> pendingBadgeType
    else -> null
  }

private fun TransactionType.sideTextTint(): ListItemSideTextTint =
  when (this) {
    Incoming -> GREEN
    Outgoing, UtxoConsolidation -> PRIMARY
  }
