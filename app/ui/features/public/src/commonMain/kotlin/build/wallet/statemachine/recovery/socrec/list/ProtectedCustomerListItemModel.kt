package build.wallet.statemachine.recovery.socrec.list

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.tokens.LabelType

fun ProtectedCustomer.listItemModel(onClick: (ProtectedCustomer) -> Unit) =
  listItemModel(useLargeLeadingAccessory = false, onClick = onClick)

fun ProtectedCustomer.listItemModel(
  useLargeLeadingAccessory: Boolean,
  onClick: (ProtectedCustomer) -> Unit,
) = ListItemModel(
  title = alias.alias,
  titleType = if (useLargeLeadingAccessory) LabelType.Body2Regular else null,
  leadingAccessory =
    ListItemAccessory.CircularCharacterAccessory.fromLetters(
      input = alias.alias,
      circleSize = if (useLargeLeadingAccessory) IconSize.Large else IconSize.Small,
      characterType = if (useLargeLeadingAccessory) LabelType.Body2Medium else LabelType.Label3
    ),
  trailingAccessory = drillIcon(tint = IconTint.On30),
  onClick = { onClick(this) }
)
