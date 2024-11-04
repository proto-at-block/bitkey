package build.wallet.statemachine.inheritance.claims.start

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Bottom sheet prompt as a final confirmation before starting a claim.
 */
internal data class StartClaimConfirmationPromptBodyModel(
  override val onBack: () -> Unit,
  val onConfirm: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Submit inheritance claim?",
      subline = "This process cannot be undone."
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.PrimaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onConfirm)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBack)
    ),
    renderContext = RenderContext.Sheet
  )
