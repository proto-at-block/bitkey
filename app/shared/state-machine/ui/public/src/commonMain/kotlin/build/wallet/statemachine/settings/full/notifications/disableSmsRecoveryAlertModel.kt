package build.wallet.statemachine.settings.full.notifications

import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.alert.ButtonAlertModel.ButtonStyle.Destructive

fun disableSmsRecoveryAlertModel(
  onKeep: () -> Unit,
  onDisable: () -> Unit,
) = ButtonAlertModel(
  title = "Are you sure you want to disable SMS recovery?",
  subline = "The more recovery channels you have, the more secure your Bitkey is.",
  onDismiss = onKeep,
  primaryButtonText = "Disable",
  onPrimaryButtonClick = onDisable,
  primaryButtonStyle = Destructive,
  secondaryButtonText = "Keep",
  onSecondaryButtonClick = onKeep
)
