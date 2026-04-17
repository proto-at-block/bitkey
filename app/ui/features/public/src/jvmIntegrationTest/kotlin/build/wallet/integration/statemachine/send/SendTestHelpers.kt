package build.wallet.integration.statemachine.send

import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel

/**
 * Selects the APPROVE option on the W3 emulated prompt selection screen.
 */
internal fun PromptSelectionFormBodyModel.clickApprove() {
  onApprove()
}
