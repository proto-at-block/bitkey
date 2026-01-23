package build.wallet.nfc.platform

import build.wallet.nfc.NfcSession

/**
 * Represents the state and flow of hardware interactions in the wallet application.
 *
 * @param R The type of the final result that will be returned when the interaction completes
 */
sealed interface HardwareInteraction<R> {
  /**
   * Interaction has ended and the resulting data was returned.
   */
  data class Completed<R>(
    /**
     * The resulting data from the hardware interaction.
     */
    val result: R,
  ) : HardwareInteraction<R>

  /**
   * Indicates that an interaction has started, but requires the user to confirm
   * on the device before the result can be retrieved. The caller must perform
   * another NFC tap after the user confirms.
   */
  data class RequiresConfirmation<R>(
    /**
     * Callable to invoke when the app is ready to perform the second NFC tap
     * to fetch the result after the user has confirmed on the device.
     */
    val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<R>,
  ) : HardwareInteraction<R>

  /**
   * Emulates on-device confirmation for fake hardware implementations.
   *
   * The UI shows a prompt with [options] simulating the device's confirmation screen.
   * After user selection, the confirmation flow continues with a second NFC tap.
   */
  data class ConfirmWithEmulatedPrompt<R>(
    /**
     * Options simulating device confirmation choices (e.g., "Approve", "Deny").
     */
    val options: List<EmulatedPromptOption<R>>,
  ) : HardwareInteraction<R>
}
