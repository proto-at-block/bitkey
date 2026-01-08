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
   * Indicates that an interaction has started, but requires additional
   * interaction with the device to continue.
   */
  data class Continuation<R>(
    /**
     * Callable to invoke when the app is ready to scan the next command.
     */
    val tryContinue: suspend (NfcSession) -> HardwareInteraction<R>,
  ) : HardwareInteraction<R>

  /**
   * Request that the app emulate a series of selectable
   * options before proceeding with the response.
   */
  data class EmulatePrompt<R>(
    /**
     * A list of options that the app should emulate, in lieu of real hardware
     * interactions.
     */
    val options: List<PromptOption<R>>,
  ) : HardwareInteraction<R>
}
