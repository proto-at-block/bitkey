package build.wallet.nfc.platform

import build.wallet.nfc.NfcSession

/**
 * Represents a selectable option to display in the app as an emulated
 * hardware interaction.
 *
 * @param T The type of result that will be returned when this option's flow completes
 */
data class PromptOption<T>(
  /**
   * Text to display in the selectable option
   */
  val name: String,
  /**
   * Action to take when the option is selected.
   *
   * When invoked, this can either return a `Completed`
   * response with data, or a new interaction to emulate.
   */
  val onSelect: suspend (NfcSession) -> HardwareInteraction<T>,
)
