package build.wallet.nfc.platform

import build.wallet.Progress
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
   * Indicates that an interaction requires chunked data transfer during the current
   * NFC session before proceeding. Used for operations like W3 transaction signing
   * where PSBT data must be transferred in chunks with progress updates.
   *
   * The [transferAndFetch] callback handles the transfer and returns the next
   * interaction state (typically [RequiresConfirmation] for operations needing
   * user approval on the device).
   */
  data class RequiresTransfer<R>(
    /**
     * Callback to perform chunked data transfer during the current NFC session.
     * The [onProgress] callback should be invoked with progress values to update
     * the UI during transfer.
     *
     * @param session The active NFC session
     * @param commands The NFC commands interface for executing transfer operations
     * @param onProgress Callback to report transfer progress
     * @return The next [HardwareInteraction] state after transfer completes
     */
    val transferAndFetch: suspend (
      session: NfcSession,
      commands: NfcCommands,
      onProgress: (Progress) -> Unit,
    ) -> HardwareInteraction<R>,
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
