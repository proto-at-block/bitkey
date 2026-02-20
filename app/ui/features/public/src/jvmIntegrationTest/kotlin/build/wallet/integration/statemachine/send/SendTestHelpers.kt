package build.wallet.integration.statemachine.send

import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel

/**
 * Selects the APPROVE option on the W3 emulated prompt selection screen.
 */
internal fun PromptSelectionFormBodyModel.clickApprove() {
  val approveIndex = options.indexOf(EmulatedPromptOption.APPROVE)
  if (approveIndex == -1) error("APPROVE option not found in options: $options")
  onOptionSelected(approveIndex)
}
