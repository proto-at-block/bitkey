import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.SecondaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal fun FoundCloudBackupForDifferentAccountModel(
  onClose: () -> Unit,
  onOverwriteExistingBackup: () -> Unit,
) = FormBodyModel(
  id = null,
  onBack = onClose,
  toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
  header = FormHeaderModel(
    headline = "This cloud account already in use with another Bitkey",
    subline = "The backup for a different Bitkey was found in the connected cloud ${cloudServiceProvider().name} account. If you're sure that backup isn't needed any longer, you can proceed to overwrite it with a new cloud backup below. Otherwise, try signing into a new cloud account that isn't already being used with another Bitkey."
  ),
  primaryButton = ButtonModel(
    text = "Overwrite existing backup",
    treatment = SecondaryDestructive,
    size = ButtonModel.Size.Footer,
    onClick = StandardClick { onOverwriteExistingBackup() }
  )
)
