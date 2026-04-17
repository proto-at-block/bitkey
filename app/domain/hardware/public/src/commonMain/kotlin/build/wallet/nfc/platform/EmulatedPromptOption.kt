package build.wallet.nfc.platform

import build.wallet.nfc.NfcSession

/**
 * Represents one selectable action for [HardwareInteraction.ConfirmWithEmulatedPrompt].
 *
 * Used by fake NfcCommands implementations to simulate device confirmation prompts.
 * After user selects an option, the confirmation flow continues and [fetchResult]
 * is called on the second NFC tap to retrieve the result.
 *
 * @param T The type of result that will be returned when this option's flow completes
 */
data class EmulatedPromptOption<T>(
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
  /**
   * A label-value pair shown in the emulated prompt sheet to provide context
   * about the operation being confirmed (e.g., "Action" to "Sign Transaction").
   */
  data class Detail(val label: String, val value: String)
}
