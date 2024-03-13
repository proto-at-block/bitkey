package build.wallet.statemachine.status

import build.wallet.analytics.events.screen.id.AppFunctionalityEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * The body model for the inactive app status screen
 *
 * onClose - The action to take when the screen is closed
 */
fun InactiveAppInfoBodyModel(onClose: () -> Unit): FormBodyModel {
  return FormBodyModel(
    id = AppFunctionalityEventTrackerScreenId.INACTIVE_APP_STATUS,
    onBack = onClose,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory.CloseAccessory(
            onClose
          )
      ),
    header =
      FormHeaderModel(
        icon = Icon.LargeIconWarningFilled,
        headline = "Your wallet was restored on another phone.",
        subline = "Select how to proceed:"
      ),
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel =
            ListGroupModel(
              style = ListGroupStyle.DIVIDER,
              items =
                immutableListOf(
                  ListItemModel(
                    title = "Use with limited functionality",
                    secondaryText = "Some features may not be available",
                    trailingAccessory = ListItemAccessory.drillIcon(),
                    onClick = onClose
                  )
                )
            )
        )
      ),
    primaryButton = null
  )
}
