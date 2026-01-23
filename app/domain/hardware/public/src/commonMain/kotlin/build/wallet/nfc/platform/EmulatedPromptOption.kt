package build.wallet.nfc.platform

import build.wallet.nfc.NfcSession

/**
 * Represents a selectable option for [HardwareInteraction.ConfirmWithEmulatedPrompt].
 *
 * Used by fake NfcCommands implementations to simulate device confirmation prompts.
 * After user selects an option, the confirmation flow continues and [fetchResult]
 * is called on the second NFC tap to retrieve the result.
 *
 * @param T The type of result that will be returned when this option's flow completes
 */
data class EmulatedPromptOption<T>(
  /**
   * Text to display for this option (e.g., "Approve", "Deny").
   */
  val name: String,
  /**
   * Callback invoked during the second NFC tap to retrieve the result.
   */
  val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
  /**
   * Called immediately when this option is selected, before continuing the flow.
   * Use this to perform side effects.
   */
  val onSelect: (suspend () -> Unit)? = null,
) {
  companion object {
    const val APPROVE = "Approve"
    const val DENY = "Deny"
  }
}
