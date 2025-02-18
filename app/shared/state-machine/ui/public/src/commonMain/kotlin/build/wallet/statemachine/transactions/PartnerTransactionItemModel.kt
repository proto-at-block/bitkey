package build.wallet.statemachine.transactions

import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint

fun PartnerTransactionItemModel(
  title: String,
  date: String,
  logoUrl: String?,
  amount: String?,
  amountEquivalent: String?,
  isPending: Boolean,
  isError: Boolean,
  onClick: () -> Unit,
  sideTextTint: ListItemSideTextTint,
) = ListItemModel(
  title = title,
  secondaryText = date,
  sideText = amount,
  secondarySideText = amountEquivalent,
  leadingAccessory = IconAccessory(
    model = IconModel(
      iconImage = when {
        logoUrl != null -> IconImage.UrlImage(logoUrl, Bitcoin)
        else -> IconImage.LocalImage(Bitcoin)
      },
      iconSize = when {
        isError || isPending -> IconSize.Custom(48)
        else -> IconSize.Large
      },
      iconBackgroundType = IconBackgroundType.Square(
        size = IconSize.Custom(48),
        color = IconBackgroundType.Square.Color.Transparent,
        cornerRadius = 0
      ),
      iconAlignmentInBackground = IconAlignmentInBackground.Center,
      badge = when {
        isError -> BadgeType.Error
        isPending -> BadgeType.Loading
        else -> null
      }
    )
  ),
  sideTextTint = sideTextTint,
  onClick = onClick
)
