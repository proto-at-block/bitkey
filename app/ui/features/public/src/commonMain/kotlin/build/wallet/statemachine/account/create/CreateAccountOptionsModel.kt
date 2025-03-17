package build.wallet.statemachine.account.create

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class CreateAccountOptionsModel(
  override val onBack: () -> Unit,
  val onUseHardwareClick: () -> Unit,
  val onUseThisDeviceClick: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header = FormHeaderModel(
      headline = "Welcome to Bitkey",
      subline = "How do you want to get started?"
    ),
    mainContentList = immutableListOf(
      ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              leadingAccessory = IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = Icon.SmallIconShieldPerson,
                  iconSize = IconSize.Small
                )
              ),
              title = "Use Bitkey hardware",
              secondaryText = "Higher security",
              onClick = onUseHardwareClick,
              trailingAccessory = drillIcon(IconTint.On30)
            ),
            ListItemModel(
              leadingAccessory = IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = Icon.SmallIconWallet,
                  iconSize = IconSize.Small
                )
              ),
              title = "Use this device",
              secondaryText = "Fast, Free",
              onClick = onUseThisDeviceClick,
              trailingAccessory = drillIcon(IconTint.On30)
            )
          ),
          style = ListGroupStyle.CARD_ITEM
        )
      )
    ),
    primaryButton = null
  )
