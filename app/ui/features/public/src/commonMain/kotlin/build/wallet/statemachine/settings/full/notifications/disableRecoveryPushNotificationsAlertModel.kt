package build.wallet.statemachine.settings.full.notifications

import build.wallet.ui.model.alert.ButtonAlertModel

fun disableRecoveryPushNotificationsAlertModel(
  onDisable: () -> Unit,
  onKeep: () -> Unit,
) = ButtonAlertModel(
  title = "Are you sure you want to disable recovery push notifications?",
  subline = "The more recovery channels you have, the more secure your Bitkey is.",
  primaryButtonText = "Disable",
  onPrimaryButtonClick = onDisable,
  primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Keep",
  onSecondaryButtonClick = onKeep,
  onDismiss = onKeep
)
