package build.wallet.statemachine.transactions

import build.wallet.statemachine.core.Icon.SmallIconArrowDown
import build.wallet.statemachine.core.Icon.SmallIconArrowUp
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
  incoming: Boolean,
  isPending: Boolean,
  onClick: () -> Unit,
) = ListItemModel(
  title = truncatedRecipientAddress,
  secondaryText = date,
  sideText = amount,
  secondarySideText = amountEquivalent,
  leadingAccessory =
    IconAccessory(
      model =
        IconModel(
          iconImage =
            if (isPending) {
              IconImage.Loader
            } else if (incoming) {
              IconImage.LocalImage(SmallIconArrowDown)
            } else {
              IconImage.LocalImage(SmallIconArrowUp)
            },
          iconSize = Small,
          iconBackgroundType = Circle(circleSize = Large)
        )
    ),
  sideTextTint =
    when {
      incoming -> GREEN
      else -> PRIMARY
    },
  onClick = onClick
)
