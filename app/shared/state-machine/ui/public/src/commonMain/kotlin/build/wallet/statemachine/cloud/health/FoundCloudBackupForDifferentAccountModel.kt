import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.cloud.RetryCloudSignInButton
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.button.ButtonModel.Treatment.SecondaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal fun FoundCloudBackupForDifferentAccountModel(
  onClose: () -> Unit,
  onOverwriteExistingBackup: () -> Unit,
  onTryAgain: () -> Unit,
  devicePlatform: DevicePlatform,
) = FormBodyModel(
  id = CloudEventTrackerScreenId.OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_BACKUP_REPAIR,
  onBack = onClose,
  toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
  header = FormHeaderModel(
    headline = "This cloud account already in use with another Bitkey",
    subline = """
      The backup for a different Bitkey was found in the connected cloud ${cloudServiceProvider().name} account.
      
      If you’re sure that backup isn’t needed any longer, you can proceed to overwrite it with a new cloud backup below.
      
      Otherwise, try signing into a new cloud account that isn’t already being used with another Bitkey.
    """.trimIndent()
  ),
  primaryButton = ButtonModel(
    text = "Overwrite existing backup",
    treatment = SecondaryDestructive,
    size = ButtonModel.Size.Footer,
    onClick = StandardClick { onOverwriteExistingBackup() }
  ),
  secondaryButton = RetryCloudSignInButton(
    androidText = "Use different Google Account",
    onTryAgain = onTryAgain,
    treatment = Secondary,
    devicePlatform = devicePlatform
  )
)

internal fun OverwriteExistingBackupConfirmationAlert(
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) = AlertModel(
  title = "Overwrite existing backup?",
  subline = "This will replace any backups that you might have on this cloud account",
  primaryButtonText = "Overwrite",
  onPrimaryButtonClick = onConfirm,
  primaryButtonStyle = AlertModel.ButtonStyle.Destructive,
  secondaryButtonText = "Cancel",
  onSecondaryButtonClick = onCancel,
  onDismiss = onCancel
)
