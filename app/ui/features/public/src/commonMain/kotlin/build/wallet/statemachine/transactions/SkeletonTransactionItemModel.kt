package build.wallet.statemachine.transactions

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel

/**
 * Creates a skeleton loading ListItemModel for transactions.
 * Shows placeholder content with loading shimmer effect.
 */
fun SkeletonTransactionItemModel() = ListItemModel(
  title = "bc1q...5678",
  titleLabel = null,
  secondaryText = "Today at 8:88",
  sideText = "$888.88",
  secondarySideText = "0.888 BTC",
  leadingAccessory = IconAccessory(
    model = IconModel(
      iconImage = IconImage.LocalImage(Icon.Bitcoin),
      iconSize = IconSize.Large,
      iconBackgroundType = IconBackgroundType.Square(
        size = IconSize.Custom(48),
        color = IconBackgroundType.Square.Color.Transparent,
        cornerRadius = 0
      ),
      iconAlignmentInBackground = IconAlignmentInBackground.Center,
      badge = BadgeType.Loading
    )
  ),
  onClick = null,
  enabled = false,
  isLoading = true
)

