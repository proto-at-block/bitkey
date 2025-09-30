package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Full screen app update modal that prompts users to update their app.
 * Users can update now or cancel to dismiss the modal for this session.
 */
data class AppUpdateModalBodyModel(
  val onUpdate: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = GeneralEventTrackerScreenId.APP_UPDATE_MODAL,
    onBack = null,
    toolbar = ToolbarModel(),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "App update required",
      subline = "A new version of the Bitkey app is required to continue. Please update to the latest version from your app store."
    ),
    primaryButton = ButtonModel(
      text = "Update now",
      treatment = Treatment.Primary,
      size = Size.Footer,
      onClick = StandardClick(onUpdate)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = Treatment.Secondary,
      size = Size.Footer,
      onClick = StandardClick(onCancel)
    )
  )
