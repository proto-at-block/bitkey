package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
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
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Content for account access options for the EAK app variant.
 */
data class EmergencyAccountAccessMoreOptionsFormBodyModel(
  override val onBack: () -> Unit,
  val onRestoreEmergencyAccessKit: (() -> Unit),
) : FormBodyModel(
    id = GeneralEventTrackerScreenId.ACCOUNT_ACCESS_MORE_OPTIONS,
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
                  icon = Icon.SmallIconRecovery,
                  iconSize = IconSize.Small
                )
              ),
              title = "Import using Emergency Access Kit",
              onClick = onRestoreEmergencyAccessKit,
              trailingAccessory = ListItemAccessory.drillIcon(IconTint.On30)
            )
          ),
          style = ListGroupStyle.CARD_ITEM
        )
      )
    ),
    primaryButton = null
  )
