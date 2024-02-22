package build.wallet.statemachine.recovery.socrec.list

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.list.ListItemModel

fun ProtectedCustomer.listItemModel(onClick: (ProtectedCustomer) -> Unit) =
  ListItemModel(
    title = alias.alias,
    leadingAccessory =
      ListItemAccessory.CircularCharacterAccessory(
        character = alias.alias.first().uppercaseChar()
      ),
    trailingAccessory = drillIcon(tint = IconTint.On30),
    onClick = { onClick(this) }
  )
