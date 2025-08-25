package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

fun FingerprintTroubleshootingSheetModel(
  onContinue: () -> Unit,
  onClosed: () -> Unit,
  eventTrackerContext: EventTrackerContext,
) = SheetModel(
  onClosed = onClosed,
  body = FingerprintTroubleshootingSheetBodyModel(
    onContinue = onContinue,
    eventTrackerContext = eventTrackerContext
  )
)

private data class FingerprintTroubleshootingSheetBodyModel(
  val onContinue: () -> Unit,
  override val eventTrackerContext: EventTrackerContext,
) : FormBodyModel(
    id = ManagingFingerprintsEventTrackerScreenId.FINGERPRINT_TROUBLESHOOTING_SHEET,
    onBack = null,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Wake your Bitkey device",
      subline = "To begin troubleshooting, press the fingerprint sensor until you see a light."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Continue",
      onClick = StandardClick(onContinue),
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet,
    eventTrackerContext = eventTrackerContext
  )
