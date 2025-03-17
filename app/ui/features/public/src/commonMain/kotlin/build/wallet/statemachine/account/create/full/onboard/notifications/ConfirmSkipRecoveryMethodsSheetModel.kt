package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class ConfirmSkipRecoveryMethodsSheetModel(
  val onCancel: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_SKIP_SHEET,
    header = FormHeaderModel(
      headline = "Continue without all recovery methods?",
      subline = "To help keep your account safe and secure, we recommend enabling all " +
        "recovery methods. You can always add these later.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton = ButtonModel(
      text = "Add recovery methods",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
    secondaryButton = ButtonModel(
      text = "Skip",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onContinue)
    ),
    renderContext = RenderContext.Sheet
  )
