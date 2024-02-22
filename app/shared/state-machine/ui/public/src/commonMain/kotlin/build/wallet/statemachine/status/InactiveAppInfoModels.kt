package build.wallet.statemachine.status

import build.wallet.analytics.events.screen.id.AppFunctionalityEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.alert.AlertModel
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
 * onSetUpNewWallet: The action to take when the user wants to set up a new wallet
 */
fun InactiveAppInfoBodyModel(
  onClose: () -> Unit,
  onSetUpNewWallet: () -> Unit,
): FormBodyModel {
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
                  ),
                  ListItemModel(
                    title = "Set up a new wallet or Restore Backup",
                    secondaryText = "Start fresh with a new Bitcoin wallet on this device.",
                    trailingAccessory = ListItemAccessory.drillIcon(),
                    onClick = onSetUpNewWallet
                  )
                )
            )
        )
      ),
    primaryButton = null
  )
}

/**
 * The alert model for setting up a new wallet and erasing the current keys from the phone
 *
 * onOverwrite - The action to take when the user wants to overwrite the current wallet
 * onCancel - The action to take when the user wants to cancel the action
 */
fun SetUpNewWalletAlert(
  onOverwrite: () -> Unit,
  onCancel: () -> Unit,
) = AlertModel(
  title = "Erase App contents?",
  subline = "This will delete the app key on this phone so you can set up a new one. You won't be able to use this app to manage the contents of this Bitkey without restoring using your Hardware.",
  onDismiss = onCancel,
  primaryButtonText = "Overwrite",
  onPrimaryButtonClick = onOverwrite,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onCancel
)
