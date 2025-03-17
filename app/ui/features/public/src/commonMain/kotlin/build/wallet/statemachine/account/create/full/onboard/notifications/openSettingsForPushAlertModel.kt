package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * App dialog directing user to open settings to modify push with the system.
 */
internal fun openSettingsForPushAlertModel(
  pushEnabled: Boolean,
  settingsOpenAction: () -> Unit,
  onClose: () -> Unit,
) = ButtonAlertModel(
  title = "Open Settings to ${
    if (pushEnabled) {
      "configure"
    } else {
      "enable"
    }
  } push notifications",
  subline = "",
  primaryButtonText = "Settings",
  secondaryButtonText = "Close",
  onDismiss = onClose,
  onPrimaryButtonClick = {
    settingsOpenAction()
  },
  onSecondaryButtonClick = onClose
)
