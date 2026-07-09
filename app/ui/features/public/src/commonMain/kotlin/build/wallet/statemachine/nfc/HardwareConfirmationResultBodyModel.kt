package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Body model for W3 two-tap confirmation result screens.
 *
 * Used for:
 * - **Pending**: User tapped before deciding on the device (prompts them to approve/deny)
 * - **Denied**: User explicitly denied on the device (acknowledges denial, allows retry)
 *
 * @param headline Operation-specific headline (e.g., "Review transaction on Bitkey")
 * @param subline Operation-specific description
 * @param buttonText Text for the acknowledgment button (e.g., "Got it")
 * @param onAcknowledge Callback when user taps the acknowledgment button
 * @param eventTrackerScreenId Screen ID for analytics tracking
 */
data class HardwareConfirmationResultBodyModel(
  val headline: String,
  val subline: String,
  val buttonText: String,
  val onAcknowledge: () -> Unit,
  val eventTrackerScreenId: EventTrackerScreenId,
) : FormBodyModel(
    id = eventTrackerScreenId,
    onBack = onAcknowledge,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onAcknowledge)),
    header = FormHeaderModel(
        iconModel = IconModel(
          icon = Icon.LargeIconWarningFilled,
          iconSize = IconSize.XLarge,
          iconTint = IconTint.Foreground
        ),
        headline = headline,
        sublineModel = LabelModel.StringModel(subline)
    ),
    primaryButton = ButtonModel(
      text = buttonText,
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onAcknowledge
    )
  )
