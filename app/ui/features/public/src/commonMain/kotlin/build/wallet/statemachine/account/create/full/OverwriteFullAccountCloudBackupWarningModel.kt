package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class OverwriteFullAccountCloudBackupWarningModel(
  val onOverwriteExistingBackup: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    header = FormHeaderModel(
      headline = "Bitkey backup found",
      subline = "This Cloud account already contains a Bitkey backup.\n\nYou can either continue and overwrite the existing backup or cancel and restore your wallet instead."
    ),
    primaryButton = ButtonModel(
      text = "Overwrite the existing backup",
      onClick = StandardClick(onOverwriteExistingBackup),
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      onClick = StandardClick(onCancel),
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer
    ),
    onBack = null,
    toolbar = null,
    id = CloudEventTrackerScreenId.OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING
  )
