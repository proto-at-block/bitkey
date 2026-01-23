package build.wallet.statemachine.cloud.health

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

internal data class ErrorCreatingBackupModel(
  val onClose: () -> Unit,
  val onRetry: () -> Unit,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.ERROR_CREATING_CLOUD_BACKUP,
    onBack = onClose,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
    header = FormHeaderModel(
      headline = "There’s an problem creating backup",
      subline = "Please try again."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Retry",
      size = Footer,
      onClick = StandardClick(onRetry)
    )
  )
