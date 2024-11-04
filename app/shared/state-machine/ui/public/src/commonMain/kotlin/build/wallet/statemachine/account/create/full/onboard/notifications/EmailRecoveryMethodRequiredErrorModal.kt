package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class EmailRecoveryMethodRequiredErrorModal(
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.RECOVERY_EMAIL_REQUIRED_ERROR_SHEET,
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "An email is required",
      subline = "To keep your Bitkey safe and secure, it's important to have a way to notify " +
        "you about any security changes or recovery events when they take place.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onCancel,
    toolbar = null,
    primaryButton =
      ButtonModel(
        text = "Continue",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel)
      ),
    renderContext = RenderContext.Sheet
  )
