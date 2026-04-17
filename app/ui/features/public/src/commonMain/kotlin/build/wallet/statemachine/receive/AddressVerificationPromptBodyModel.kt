package build.wallet.statemachine.receive

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.ReceiveEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Body model for the address verification prompt screen shown to W3 users
 * before displaying the receive QR code. Prompts the user to verify the
 * address on their Bitkey device or skip to the QR code.
 */
data class AddressVerificationPromptBodyModel(
  override val onBack: () -> Unit,
  val onVerify: () -> Unit,
  val onSkip: () -> Unit,
  val headline: String = "Do you want to review your Bitkey address?",
  val subline: String = "Display your address on your Bitkey for verification.",
  val screenId: EventTrackerScreenId = ReceiveEventTrackerScreenId.RECEIVE_ADDRESS_VERIFICATION,
) : FormBodyModel(
    id = screenId,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack)
    ),
    header = FormHeaderModel(
      headline = headline,
      subline = subline
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Review on Bitkey",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onVerify() }
    ),
    secondaryButton = ButtonModel(
      text = "Skip",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onSkip() }
    )
  )
