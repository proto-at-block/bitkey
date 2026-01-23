package build.wallet.statemachine.cloud.health

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class ProblemWithCloudBackupFormModel(
  val onClose: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.CLOUD_BACKUP_PROBLEM,
    onBack = onClose,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
    header = FormHeaderModel(
      headline = "There’s an problem with your ${cloudServiceProvider().name} backup",
      subline = "Tap your Bitkey device to create a new App Key backup and save it to your ${cloudServiceProvider().name} account"
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      size = Footer,
      onClick = StandardClick(onContinue)
    )
  )
