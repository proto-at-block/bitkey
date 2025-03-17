package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class DestructiveInheritanceActionBodyModel(
  val headline: String,
  val subline: String,
  val primaryButtonText: String,
  val onClose: () -> Unit,
  val onPrimaryClick: () -> Unit,
  val isLoading: Boolean = false,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.CancelingClaim,
    onBack = onClose,
    renderContext = RenderContext.Sheet,
    toolbar = null,
    header = FormHeaderModel(
      headline = headline,
      subline = subline
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = primaryButtonText,
      onClick = StandardClick {
        onPrimaryClick()
      },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.PrimaryDestructive,
      isLoading = isLoading
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      onClick = SheetClosingClick { onClose() },
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      isEnabled = !isLoading
    )
  )
